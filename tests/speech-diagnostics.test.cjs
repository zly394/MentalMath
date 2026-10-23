const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { resolve } = require("node:path");
const { test } = require("node:test");
const { runInNewContext } = require("node:vm");
const { webcrypto } = require("node:crypto");
const source = readFileSync(resolve(__dirname,
  "..", "composeApp", "src", "jsMain", "resources", "speech-diagnostics.js"), "utf8");

function setup(enabled = true) {
  let time = 0;
  let timer;
  let failUploads = false;
  const requests = [];
  const beacons = [];
  const listeners = {};
  const elements = [];
  const document = {
    body: { appendChild: element => elements.push(element) },
    createElement: () => ({ style: {} }),
    addEventListener: (type, listener) => { listeners[type] = listener; },
    visibilityState: "visible"
  };
  const window = {
    isSecureContext: false,
    SpeechRecognition: function () {},
    setTimeout, clearTimeout,
    setInterval: callback => { timer = callback; },
    addEventListener: (type, listener) => { listeners[type] = listener; }
  };
  const context = {
    window, document, crypto: webcrypto, Blob, URLSearchParams, AbortController,
    location: { search: enabled ? "?speechDebug=1" : "" },
    performance: { now: () => time },
    console: { warn() {} },
    navigator: {
      userAgent: "test-browser",
      sendBeacon: (url, data) => { beacons.push({ url, data }); return true; }
    },
    fetch: async (url, options) => {
      requests.push({ url, body: JSON.parse(options.body) });
      return { ok: !failUploads, status: failUploads ? 503 : 200 };
    }
  };
  runInNewContext(source, context);
  return {
    window, requests, listeners, beacons, elements,
    flush: () => timer(),
    tick: value => { time = value; },
    fail: value => { failUploads = value; }
  };
}

function recognition() {
  const listeners = {};
  return {
    starts: 0, aborts: 0,
    start() { this.starts++; },
    abort() { this.aborts++; },
    addEventListener(type, listener) { listeners[type] = listener; },
    dispatch(type, event = {}) { listeners[type](event); }
  };
}

test("disabled diagnostics leave the page and speech API untouched", () => {
  const page = setup(false);
  assert.equal(page.window.mentalMathDiagnostics, undefined);
  assert.equal(page.requests.length, 0);
  assert.equal(page.elements.length, 0);
});

test("records timing without reading transcripts and preserves recognition calls", async () => {
  const page = setup();
  await new Promise(setImmediate);
  const diagnostics = page.window.mentalMathDiagnostics;
  diagnostics.record("question_start", "0");
  const rec = recognition();
  diagnostics.observeRecognition(rec);
  page.tick(100);
  rec.start();
  page.tick(300);
  rec.dispatch("audiostart");
  const result = { isFinal: false };
  Object.defineProperty(result, "transcript", {
    get() { throw new Error("Must not read speech text"); }
  });
  page.tick(600);
  rec.dispatch("result", { resultIndex: 0, results: [result] });
  diagnostics.record("question_start", "1");
  rec.abort();
  rec.dispatch("end");
  await page.flush();
  const events = page.requests.flatMap(request => request.body.events);
  assert.equal(rec.starts, 1);
  assert.equal(rec.aborts, 1);
  assert.equal(events.find(event => event.event === "recognition_audiostart").atMs, 300);
  assert.equal(events.find(event => event.event === "recognition_result").detail, "interim");
  assert.equal(events.find(event => event.event === "recognition_end").question, 1);
  assert.equal(events.filter(event => event.event === "question_start")[1].question, 2);
  assert.match(page.elements[0].textContent, /非安全连接/);
});

test("failed uploads retain events for retry with stable IDs", async () => {
  const page = setup();
  await new Promise(setImmediate);
  page.window.mentalMathDiagnostics.record("question_start", "0");
  page.fail(true);
  await page.flush();
  const failed = page.requests.at(-1).body.events;
  assert.match(page.elements[0].textContent, /回传失败/);
  page.fail(false);
  await page.flush();
  assert.deepEqual(page.requests.at(-1).body.events, failed);
  assert.doesNotMatch(page.elements[0].textContent, /回传失败/);
});

test("pagehide sends pending events to the same local endpoint", async () => {
  const page = setup();
  await new Promise(setImmediate);
  page.window.mentalMathDiagnostics.record("session_stop");
  page.listeners.pagehide();
  assert.equal(page.beacons[0].url, "/__speech_diagnostics/events");
  const data = JSON.parse(await page.beacons[0].data.text());
  assert.equal(data.events[0].event, "session_stop");
});
