(() => {
  "use strict";
  if (new URLSearchParams(location.search).get("speechDebug") !== "1") return;

  const session = Date.now().toString(36) + "-" +
    Array.from(crypto.getRandomValues(new Uint8Array(8)),
      byte => byte.toString(16).padStart(2, "0")).join("");
  const endpoint = "/__speech_diagnostics/events";
  const queue = [];
  let sequence = 0;
  let question = 0;
  let recognitionId = 0;
  let uploading = false;
  let uploaded = 0;
  let failure = "";
  const status = document.createElement("div");
  status.style.cssText = "position:fixed;bottom:2px;left:4px;right:4px;z-index:99999;" +
    "font:11px sans-serif;background:#fff4cc;color:#222;padding:3px;pointer-events:none";
  document.body.appendChild(status);

  function updateStatus() {
    status.textContent = "语音诊断 " + session + " · 已回传 " + uploaded +
      " 条" + (window.isSecureContext ? "" : " · 非安全连接，语音可能不可用") +
      (failure ? " · " + failure : "");
  }

  function emit(event, detail = "", run = 0, q = question) {
    if (queue.length >= 1000) {
      failure = "日志队列已满，请检查电脑服务";
      updateStatus();
      return;
    }
    queue.push({
      id: ++sequence, event, detail, question: q, recognition: run,
      atMs: Math.round(performance.now() * 100) / 100
    });
    updateStatus();
  }

  async function flush() {
    if (uploading || queue.length === 0) return;
    uploading = true;
    const batch = queue.slice(0, 100);
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), 5000);
    try {
      const response = await fetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ session, events: batch }),
        cache: "no-store",
        keepalive: true,
        signal: controller.signal
      });
      if (!response.ok) throw new Error("HTTP " + response.status);
      queue.splice(0, batch.length);
      uploaded += batch.length;
      failure = "";
    } catch (error) {
      failure = "回传失败：" + error.message;
      console.warn("[SpeechDiagnostics] Upload failed", error);
    } finally {
      window.clearTimeout(timeout);
      uploading = false;
      updateStatus();
    }
  }

  window.mentalMathDiagnostics = {
    record(event, detail = "") {
      if (event === "question_start") question++;
      emit(event, detail);
    },
    observeRecognition(recognition) {
      const run = ++recognitionId;
      const q = question;
      const start = recognition.start;
      const abort = recognition.abort;
      recognition.start = function (...args) {
        emit("recognition_start_call", "", run, q);
        return start.apply(this, args);
      };
      recognition.abort = function (...args) {
        emit("recognition_abort_call", "", run, q);
        return abort.apply(this, args);
      };
      [
        "start", "audiostart", "soundstart", "speechstart", "speechend",
        "soundend", "audioend", "result", "nomatch", "error", "end"
      ].forEach(type => {
        recognition.addEventListener(type, event => {
          let detail = "";
          if (type === "error") detail = event.error;
          if (type === "result") {
            detail = event.results[event.resultIndex].isFinal ? "final" : "interim";
          }
          emit("recognition_" + type, detail, run, q);
        });
      });
    }
  };

  emit("environment", JSON.stringify({
    secureContext: window.isSecureContext,
    speechSupported: !!(window.SpeechRecognition || window.webkitSpeechRecognition),
    userAgent: navigator.userAgent,
    standalone: navigator.standalone === true
  }));
  document.addEventListener("visibilitychange", () => {
    emit("visibility", document.visibilityState);
    void flush();
  });
  window.addEventListener("pagehide", () => {
    if (queue.length) {
      // Event IDs let the receiver deduplicate a beacon overlapping an in-flight fetch.
      const body = new Blob(
        [JSON.stringify({ session, events: queue.slice(0, 100) })],
        { type: "application/json" }
      );
      if (!navigator.sendBeacon(endpoint, body)) {
        console.warn("[SpeechDiagnostics] Final upload was not queued");
      }
    }
  });
  window.setInterval(flush, 1000);
  void flush();
})();
