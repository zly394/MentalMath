import http.client
import json
from pathlib import Path
import sys
import tempfile
import threading
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import serve_pwa


def event(identifier, name, time, detail="", question=1, recognition=1):
    return {
        "id": identifier, "event": name, "atMs": time, "detail": detail,
        "question": question, "recognition": recognition,
    }


class DiagnosticsTests(unittest.TestCase):
    def test_summary_measures_startup_and_forced_timeout(self):
        events = [
            event(1, "question_start", 0, recognition=0),
            event(2, "countdown_start", 600, recognition=0),
            event(3, "recognition_start_call", 610),
            event(4, "recognition_audiostart", 810),
            event(5, "recognition_speechstart", 1000),
            event(6, "voice_detected", 1001, recognition=0),
            event(7, "recognition_wait_timeout", 2226, recognition=0),
            event(8, "question_settled", 2227, "TIMEOUT", recognition=0),
            event(9, "recognition_abort_call", 2228),
            event(10, "recognition_end", 2230),
        ]
        summary = serve_pwa.summarize(events + events)
        self.assertEqual(summary["event_count"], 10)
        self.assertEqual(summary["forced_recognition_timeouts"], 1)
        self.assertEqual(summary["start_to_audio"]["p50_ms"], 200)
        self.assertEqual(summary["speech_to_first_result"]["samples"], 0)
        self.assertEqual(summary["questions"][0]["voice_to_forced_timeout_ms"], 1225)
        self.assertFalse(summary["recognitions"][0]["ended_without_result"])

    def test_natural_end_without_result_and_error(self):
        summary = serve_pwa.summarize([
            event(1, "recognition_start_call", 0),
            event(2, "recognition_error", 50, "not-allowed"),
            event(3, "recognition_end", 60),
        ])
        self.assertTrue(summary["recognitions"][0]["ended_without_result"])
        self.assertEqual(summary["errors"], {"not-allowed": 1})

    def test_success_and_late_events_remain_on_original_question(self):
        summary = serve_pwa.summarize([
            event(1, "recognition_start_call", 0),
            event(2, "recognition_audiostart", 40),
            event(3, "recognition_speechstart", 500),
            event(4, "voice_detected", 501, recognition=0),
            event(5, "recognition_result", 700, "interim"),
            event(6, "number_parsed", 701, recognition=0),
            event(7, "question_settled", 702, "CORRECT", recognition=0),
            event(8, "question_start", 1200, question=2, recognition=0),
            event(9, "recognition_end", 1250),
        ])
        self.assertEqual(summary["speech_to_first_result"]["p95_ms"], 200)
        self.assertEqual(summary["questions"][0]["voice_to_parsed_ms"], 200)
        self.assertEqual(summary["recognitions"][0]["question"], 1)
        self.assertEqual(summary["questions"][1]["question"], 2)

    def test_validation_rejects_paths_extra_fields_and_nonfinite_time(self):
        valid = {"session": "test-1", "events": [event(1, "question_start", 0)]}
        self.assertEqual(serve_pwa.validate_batch(valid)[0], "test-1")
        for invalid in (
            {**valid, "session": "../escape"},
            {**valid, "events": []},
            {**valid, "events": [{**valid["events"][0], "transcript": "private"}]},
            {**valid, "events": [{**valid["events"][0], "atMs": float("nan")}]},
            {**valid, "events": [{**valid["events"][0], "event": []}]},
        ):
            with self.subTest(payload=invalid):
                with self.assertRaises(ValueError):
                    serve_pwa.validate_batch(invalid)

    def test_http_persists_reports_and_rejects_cross_origin_upload(self):
        with tempfile.TemporaryDirectory() as directory:
            server = serve_pwa.http.server.ThreadingHTTPServer(
                ("127.0.0.1", 0), serve_pwa.CustomHandler)
            server.log_dir = Path(directory)
            server.log_lock = threading.Lock()
            server.ca_certificate = b"public-certificate"
            worker = threading.Thread(target=server.serve_forever)
            worker.start()
            connection = http.client.HTTPConnection(*server.server_address)
            payload = json.dumps({"session": "phone", "events": [
                event(1, "recognition_error", 0, "network")
            ]})
            try:
                connection.request("POST", serve_pwa.EVENTS_PATH, payload,
                                   {"Content-Type": "application/json"})
                response = connection.getresponse()
                self.assertEqual(response.status, 200)
                response.read()
                self.assertTrue((Path(directory) / "phone.jsonl").is_file())
                connection.request("GET", serve_pwa.SUMMARY_PATH)
                response = connection.getresponse()
                self.assertEqual(response.status, 200)
                self.assertEqual(json.loads(response.read())["phone"]["errors"], {"network": 1})
                connection.request("GET", serve_pwa.CA_PATH)
                response = connection.getresponse()
                self.assertEqual(response.read(), b"public-certificate")
                connection.request("POST", serve_pwa.EVENTS_PATH, payload, {
                    "Content-Type": "application/json", "Origin": "https://unrelated.example",
                })
                response = connection.getresponse()
                self.assertEqual(response.status, 403)
                response.read()
                server.log_dir = None
                connection.request("GET", serve_pwa.SUMMARY_PATH)
                response = connection.getresponse()
                self.assertEqual(response.status, 404)
                response.read()
            finally:
                connection.close()
                server.shutdown()
                server.server_close()
                worker.join()


if __name__ == "__main__":
    unittest.main()
