"""Local PWA hosting with optional speech timing diagnostics.

Build with: gradlew.bat :composeApp:wasmJsBrowserDistribution
Run with: python serve_pwa.py --port 8080 --diagnostics
Open: http://<LAN-IP>:8080/?speechDebug=1
Read: /__speech_diagnostics/summary

For iPhone speech recognition, use --cert <server.pem> --key <server.key>
on an HTTPS port. --ca-cert <public-ca.pem> exposes only the public CA at
/__speech_diagnostics/ca.cer for installation on the phone. Enable full trust
in iOS Certificate Trust Settings; bypassing a browser warning is not enough.
Keep all private keys outside the public PWA directory and remove the local
CA profile from the phone after testing.

Diagnostics are off unless both --diagnostics and ?speechDebug=1 are used.
Only event timing, outcomes, error codes and browser metadata are collected;
audio and recognized text are never included. Logs default to
build/speech-diagnostics and stay on this machine. Remove the query parameter
and reload to disable client collection. Live endpoints are not cached.
"""

import argparse
from collections import Counter
from datetime import datetime, timezone
import http.server
import json
import math
from pathlib import Path
import re
import ssl
import threading
from urllib.parse import urlsplit


ROOT = Path(__file__).resolve().parent
DIRECTORY = ROOT / "composeApp" / "build" / "dist" / "wasmJs" / "productionExecutable"
EVENTS_PATH = "/__speech_diagnostics/events"
SUMMARY_PATH = "/__speech_diagnostics/summary"
CA_PATH = "/__speech_diagnostics/ca.cer"
MAX_BODY = 65536
EVENT_NAMES = {
    "environment", "visibility", "session_start", "session_stop",
    "question_start", "countdown_start", "answer_timeout",
    "recognition_wait_timeout", "voice_detected", "number_parsed",
    "recognition_failure", "question_settled",
    "listening_preparing", "listening_ready", "listening_start_timeout",
} | {
    "recognition_" + name for name in (
        "start_call", "abort_call", "start", "audiostart", "soundstart",
        "speechstart", "speechend", "soundend", "audioend", "result",
        "nomatch", "error", "end",
    )
}


def validate_batch(payload):
    if not isinstance(payload, dict) or set(payload) != {"session", "events"}:
        raise ValueError("Expected session and events")
    session = payload["session"]
    if not isinstance(session, str) or not re.fullmatch(r"[A-Za-z0-9_-]{1,80}", session):
        raise ValueError("Invalid session")
    events = payload["events"]
    if not isinstance(events, list) or not 1 <= len(events) <= 100:
        raise ValueError("Expected 1 to 100 events")
    fields = {"id", "event", "detail", "question", "recognition", "atMs"}
    for event in events:
        if not isinstance(event, dict) or set(event) != fields:
            raise ValueError("Invalid event fields")
        if not isinstance(event["event"], str) or event["event"] not in EVENT_NAMES:
            raise ValueError("Unknown event")
        for key in ("id", "question", "recognition"):
            if type(event[key]) is not int or not 0 <= event[key] <= 1_000_000:
                raise ValueError("Invalid event identifier")
        if (type(event["atMs"]) not in (int, float)
                or not math.isfinite(event["atMs"]) or event["atMs"] < 0):
            raise ValueError("Invalid timestamp")
        if not isinstance(event["detail"], str) or len(event["detail"]) > 600:
            raise ValueError("Invalid detail")
    return session, events


def summarize(events):
    events = sorted({event["id"]: event for event in events}.values(),
                    key=lambda event: event["id"])
    questions = {}
    runs = {}
    environment = None

    def elapsed(items, start, end):
        first = next((item["atMs"] for item in items if item["event"] == start), None)
        last = next((item["atMs"] for item in items if item["event"] == end), None)
        return round(last - first, 2) if first is not None and last is not None else None

    for event in events:
        if event["event"] == "environment":
            environment = event["detail"]
        if event["question"]:
            questions.setdefault(event["question"], []).append(event)
        if event["recognition"]:
            runs.setdefault(event["recognition"], []).append(event)
    rows = []
    for number, items in questions.items():
        outcomes = [item["detail"] for item in items if item["event"] == "question_settled"]
        rows.append({
            "question": number,
            "outcome": outcomes[-1] if outcomes else None,
            "countdown_to_voice_ms": elapsed(items, "countdown_start", "voice_detected"),
            "voice_to_parsed_ms": elapsed(items, "voice_detected", "number_parsed"),
            "voice_to_forced_timeout_ms": elapsed(items, "voice_detected", "recognition_wait_timeout"),
            "forced_recognition_timeout": any(
                item["event"] == "recognition_wait_timeout" for item in items),
        })
    recognition_rows = [{
        "recognition": number,
        "question": items[0]["question"],
        "start_to_audio_ms": elapsed(items, "recognition_start_call", "recognition_audiostart"),
        "speech_to_first_result_ms": elapsed(items, "recognition_speechstart", "recognition_result"),
        "ended_without_result": (
            any(item["event"] == "recognition_end" for item in items)
            and not any(item["event"] in ("recognition_result", "recognition_abort_call")
                        for item in items)),
        "aborted_by_app": any(item["event"] == "recognition_abort_call" for item in items),
        "errors": [item["detail"] for item in items if item["event"] == "recognition_error"],
    } for number, items in runs.items()]

    def distribution(values):
        values = sorted(value for value in values if value is not None)
        if not values:
            return {"samples": 0, "p50_ms": None, "p95_ms": None}
        return {
            "samples": len(values),
            "p50_ms": values[math.ceil(len(values) * 0.5) - 1],
            "p95_ms": values[math.ceil(len(values) * 0.95) - 1],
        }

    return {
        "environment": environment,
        "event_count": len(events),
        "settled_questions": sum(row["outcome"] is not None for row in rows),
        "forced_recognition_timeouts": sum(row["forced_recognition_timeout"] for row in rows),
        "errors": dict(Counter(
            item["detail"] for item in events if item["event"] == "recognition_error")),
        "start_to_audio": distribution(row["start_to_audio_ms"] for row in recognition_rows),
        "speech_to_first_result": distribution(
            row["speech_to_first_result_ms"] for row in recognition_rows),
        "questions": rows,
        "recognitions": recognition_rows,
    }


class CustomHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(DIRECTORY), **kwargs)

    def end_headers(self):
        self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
        self.send_header("Pragma", "no-cache")
        self.send_header("Expires", "0")
        super().end_headers()

    def guess_type(self, path):
        return {
            ".wasm": "application/wasm",
            ".js": "application/javascript",
            ".ttf": "font/ttf",
        }.get(Path(path).suffix) or super().guess_type(path)

    def send_json(self, status, payload):
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        if urlsplit(self.path).path != EVENTS_PATH or self.server.log_dir is None:
            self.send_error(404)
            return
        origin = self.headers.get("Origin")
        scheme = "https" if isinstance(self.connection, ssl.SSLSocket) else "http"
        if origin and origin != f"{scheme}://{self.headers.get('Host')}":
            self.send_error(403, "Same-origin requests only")
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self.send_error(400, "Invalid Content-Length")
            return
        if not 0 < length <= MAX_BODY:
            self.send_error(413)
            return
        if self.headers.get_content_type() != "application/json":
            self.send_error(415)
            return
        try:
            payload = json.loads(self.rfile.read(length))
            session, events = validate_batch(payload)
        except (ValueError, UnicodeDecodeError):
            self.send_error(400, "Invalid diagnostics batch")
            return
        received_at = datetime.now(timezone.utc).isoformat()
        try:
            with self.server.log_lock:
                with (self.server.log_dir / f"{session}.jsonl").open("a", encoding="utf-8") as log:
                    for event in events:
                        log.write(json.dumps(
                            {**event, "receivedAt": received_at}, ensure_ascii=False) + "\n")
        except OSError as error:
            self.log_error("Cannot persist diagnostics: %s", error)
            self.send_json(500, {"error": "Cannot persist diagnostics"})
            return
        self.send_json(200, {"accepted": len(events)})

    def do_GET(self):
        if urlsplit(self.path).path == CA_PATH:
            if self.server.ca_certificate is None:
                self.send_error(404)
                return
            self.send_response(200)
            self.send_header("Content-Type", "application/x-x509-ca-cert")
            self.send_header("Content-Disposition", 'attachment; filename="MentalMath-Diagnostic-CA.cer"')
            self.send_header("Content-Length", str(len(self.server.ca_certificate)))
            self.end_headers()
            self.wfile.write(self.server.ca_certificate)
            return
        if urlsplit(self.path).path != SUMMARY_PATH:
            if urlsplit(self.path).path.startswith("/__speech_diagnostics/"):
                self.send_error(404)
                return
            super().do_GET()
            return
        if self.server.log_dir is None:
            self.send_error(404)
            return
        sessions = {}
        try:
            with self.server.log_lock:
                for path in self.server.log_dir.glob("*.jsonl"):
                    with path.open(encoding="utf-8") as log:
                        sessions[path.stem] = summarize([json.loads(line) for line in log])
        except (OSError, ValueError) as error:
            self.log_error("Cannot read diagnostics: %s", error)
            self.send_json(500, {"error": "Cannot read diagnostics"})
            return
        self.send_json(200, sessions)


def main():
    parser = argparse.ArgumentParser(
        description="Serve the PWA; add ?speechDebug=1 to collect local speech timings.",
        epilog="HTTPS on iPhone requires a trusted certificate. HTTP LAN origins are not secure contexts.")
    parser.add_argument("--port", type=int, default=8090)
    parser.add_argument("--bind", default="0.0.0.0")
    parser.add_argument("--diagnostics", action="store_true", help="Enable local diagnostic uploads")
    parser.add_argument("--log-dir", type=Path, default=ROOT / "build" / "speech-diagnostics")
    parser.add_argument("--cert", type=Path, help="HTTPS certificate PEM (must be trusted on iPhone)")
    parser.add_argument("--key", type=Path, help="HTTPS private key PEM")
    parser.add_argument("--ca-cert", type=Path, help="Public CA PEM to download for iPhone trust setup")
    args = parser.parse_args()
    if bool(args.cert) != bool(args.key):
        parser.error("--cert and --key must be supplied together")
    if not (DIRECTORY / "index.html").is_file():
        parser.error(f"PWA build missing: {DIRECTORY}")
    context = None
    if args.cert:
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(args.cert, args.key)
    ca_certificate = None
    if args.ca_cert:
        ca_certificate = ssl.PEM_cert_to_DER_cert(args.ca_cert.read_text(encoding="ascii"))
    log_dir = args.log_dir.resolve() if args.diagnostics else None
    if log_dir:
        log_dir.mkdir(parents=True, exist_ok=True)
        if log_dir.is_relative_to(DIRECTORY.resolve()):
            parser.error("--log-dir must be outside the public PWA directory")
    with http.server.ThreadingHTTPServer((args.bind, args.port), CustomHandler) as server:
        server.log_dir = log_dir
        server.log_lock = threading.Lock()
        server.ca_certificate = ca_certificate
        if context:
            server.socket = context.wrap_socket(server.socket, server_side=True)
        scheme = "https" if context else "http"
        print(f"Serving {DIRECTORY} on {scheme}://{args.bind}:{args.port}", flush=True)
        if log_dir:
            print(f"Diagnostics: ?speechDebug=1; logs: {log_dir}", flush=True)
            print(f"Summary: {SUMMARY_PATH}", flush=True)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
