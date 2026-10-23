// Run against a built PWA: PWA_TEST_URL, PLAYWRIGHT_CORE_PATH and optional PWA_CHROME_PATH.
const assert = require("node:assert/strict");
const { chromium } = require(process.env.PLAYWRIGHT_CORE_PATH || "playwright-core");

(async () => {
  const browser = await chromium.launch({
    ...(process.env.PWA_CHROME_PATH
      ? { executablePath: process.env.PWA_CHROME_PATH } : { channel: "chrome" }),
    headless: true
  });
  try {
    for (const scenario of [
      "delayed-ready", "denied", "timeout", "exit", "switch", "keypad",
      "audio-handoff", "audio-failure"
    ]) {
      const context = await browser.newContext({
        ignoreHTTPSErrors: true, serviceWorkers: "block",
        viewport: { width: 390, height: 844 }
      });
      try {
        await context.addInitScript(mode => {
          Object.defineProperty(navigator, "mediaDevices", {
            configurable: true,
            value: { getUserMedia: async () => ({ getTracks: () => [{ stop() {} }] }) }
          });
          window.testAudioOperations = [];
          let sessionType = "auto";
          Object.defineProperty(navigator, "audioSession", {
            configurable: true,
            value: {
              get type() { return sessionType; },
              set type(value) { sessionType = value; }
            }
          });
          window.testRecognizers = [];
          const sourceStart = AudioBufferSourceNode.prototype.start;
          AudioBufferSourceNode.prototype.start = function (...args) {
            window.testAudioOperations.push({
              event: "playback_start",
              at: performance.now(),
              session: sessionType,
              captureActive: window.testRecognizers.some(item => item.captureActive)
            });
            return sourceStart.apply(this, args);
          };
          window.SpeechRecognition = class extends EventTarget {
            constructor() {
              super();
              this.cancelled = false;
              window.testRecognizers.push(this);
            }
            emit(type, detail = {}) {
              const event = Object.assign(new Event(type), detail);
              this.dispatchEvent(event);
              this["on" + type]?.(event);
            }
            start() {
              this.captureActive = true;
              if (mode === "delayed-ready") {
                setTimeout(() => {
                  if (this.cancelled) return;
                  this.emit("audiostart");
                  this.emit("audiostart");
                  setTimeout(() => {
                    if (this.cancelled) return;
                    this.emit("speechstart");
                    this.emit("result", {
                      resultIndex: 0,
                      results: [Object.assign([{ transcript: "一" }], { isFinal: false })]
                    });
                  }, 500);
                }, 2500);
              } else if (mode === "denied") {
                setTimeout(() => this.emit("error", { error: "not-allowed" }), 100);
              } else if (mode === "audio-handoff" || mode === "audio-failure") {
                setTimeout(() => {
                  if (this.cancelled) return;
                  this.emit("audiostart");
                  setTimeout(() => {
                    if (this.cancelled) return;
                    this.emit("result", {
                      resultIndex: 0,
                      results: [Object.assign([{ transcript: "一" }], { isFinal: true })]
                    });
                  }, 100);
                }, 30);
              }
            }
            abort() {
              this.cancelled = true;
              if (mode === "audio-failure") return;
              setTimeout(() => {
                this.captureActive = false;
                window.testAudioOperations.push({ event: "recognition_end", at: performance.now() });
                this.emit("end");
              }, mode === "audio-handoff" ? 1400 : 50);
            }
          };
        }, scenario);
        const page = await context.newPage();
        const errors = [];
        page.on("pageerror", error => errors.push(error.stack));
        await page.route("**/__speech_diagnostics/events", route =>
          route.fulfill({ status: 200, contentType: "application/json", body: "{}" }));
        const fontReady = page.waitForEvent("console", {
          predicate: message => message.text().includes("Custom font successfully preloaded"),
          timeout: 20000
        });
        const url = new URL(process.env.PWA_TEST_URL || "https://127.0.0.1:8443/");
        url.searchParams.set("speechDebug", "1");
        await Promise.all([page.goto(url.toString()), fontReady]);
        await page.waitForTimeout(300);
        await page.evaluate(() => {
          window.testEvents = [];
          const diagnostics = window.mentalMathDiagnostics;
          const record = diagnostics.record;
          diagnostics.record = (event, detail) => {
            window.testEvents.push({ event, detail, at: performance.now() });
            record(event, detail);
          };
        });
        if (scenario === "keypad") await page.mouse.click(280, 525);
        await page.mouse.click(195, 801);
        const waitEvent = (name, timeout = 10000) => page.waitForFunction(
          name => window.testEvents.some(item => item.event === name), name, { timeout });
        const events = () => page.evaluate(() => window.testEvents);
        if (scenario === "keypad") {
          await waitEvent("countdown_start");
          assert.equal((await events()).some(item => item.event === "listening_preparing"), false);
          assert.equal(await page.evaluate(() => window.testRecognizers.length), 0);
        } else {
          await waitEvent("listening_preparing");
          if (scenario === "delayed-ready") {
            await page.waitForTimeout(2100);
            assert.equal((await events()).some(item => item.event === "countdown_start"), false);
            assert.equal((await events()).some(item => item.event === "question_settled"), false);
            await waitEvent("listening_ready");
            const firstFrame = await page.screenshot({
              clip: { x: 0, y: 0, width: 390, height: 740 }
            });
            await page.waitForTimeout(180);
            const secondFrame = await page.screenshot({
              clip: { x: 0, y: 0, width: 390, height: 740 }
            });
            assert.notDeepEqual(firstFrame, secondFrame, "countdown must repaint without input");
            await waitEvent("number_parsed");
            const recorded = await events();
            assert.equal(recorded.filter(item => item.event === "countdown_start").length, 1);
            assert.ok(recorded.find(item => item.event === "listening_ready").at -
              recorded.find(item => item.event === "listening_preparing").at >= 2400);
          } else if (scenario === "denied") {
            await waitEvent("recognition_failure");
            await waitEvent("countdown_start");
            await page.mouse.click(55, 660);
            await waitEvent("question_settled");
          } else if (scenario === "timeout") {
            await waitEvent("listening_start_timeout", 35000);
            await waitEvent("countdown_start");
            assert.equal((await events()).some(item => item.event === "question_settled"), false);
          } else if (scenario === "exit") {
            await page.mouse.click(40, 35);
            await waitEvent("session_stop");
            await page.evaluate(() => window.testRecognizers[0].emit("audiostart"));
            await page.waitForTimeout(200);
            assert.equal((await events()).some(item => item.event === "countdown_start"), false);
          } else if (scenario === "switch") {
            await page.mouse.click(320, 35);
            await waitEvent("countdown_start");
            await page.evaluate(() => window.testRecognizers[0].emit("audiostart"));
            await page.waitForTimeout(200);
            assert.equal((await events()).filter(item => item.event === "countdown_start").length, 1);
          } else if (scenario === "audio-handoff") {
            await waitEvent("question_settled");
            await page.waitForFunction(() =>
              window.testAudioOperations.filter(item => item.event === "playback_start").length >= 2);
            const operations = await page.evaluate(() => window.testAudioOperations);
            const playback = operations.filter(item => item.event === "playback_start");
            assert.equal(playback[1].captureActive, false);
            assert.equal(playback[1].session, "playback");
            assert.ok(operations.find(item => item.event === "recognition_end").at <= playback[1].at);
            assert.ok((await events()).some(item => item.event === "recognition_release_wait"));
          } else if (scenario === "audio-failure") {
            await waitEvent("playback_failure");
            await page.waitForFunction(() =>
              window.testEvents.filter(item => item.event === "countdown_start").length === 2);
            assert.equal(await page.evaluate(() => window.testRecognizers.length), 1);
            assert.equal(await page.evaluate(() =>
              window.testAudioOperations.filter(item => item.event === "playback_start").length), 1);
            await page.mouse.click(55, 660);
            await page.waitForFunction(() =>
              window.testEvents.filter(item => item.event === "question_settled").length === 2);
          }
        }
        assert.deepEqual(errors, []);
        console.log("PASS", scenario);
      } finally {
        await context.close();
      }
    }
  } finally {
    await browser.close();
  }
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
