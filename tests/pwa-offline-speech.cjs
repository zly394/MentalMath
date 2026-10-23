// Supply a local, synthetic Chinese digit WAV through PWA_TEST_AUDIO_PATH.
const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_CORE_PATH || 'playwright-core');

(async () => {
  assert.ok(process.env.PWA_TEST_AUDIO_PATH, 'PWA_TEST_AUDIO_PATH must identify a synthetic Chinese digit WAV');
  const browser = await chromium.launch({
    channel: 'chrome', headless: true,
    args: [
      '--ignore-certificate-errors',
      '--autoplay-policy=no-user-gesture-required',
      '--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream',
      `--use-file-for-fake-audio-capture=${process.env.PWA_TEST_AUDIO_PATH}`
    ]
  });
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: true });
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    page.on('console', message => {
      if (message.type() === 'error') console.error(message.text());
    });
    const url = process.env.PWA_TEST_URL || 'https://127.0.0.1:8443/';
    await page.goto(url);
    await page.waitForFunction(() => globalThis.mentalMathVosk?.Model, null, { timeout: 30000 });
    await page.evaluate(async () => {
      await Promise.race([
        navigator.serviceWorker.ready,
        new Promise((_, reject) => setTimeout(() => reject(new Error('Service worker installation timed out')), 30000))
      ]);
      if (!navigator.serviceWorker.controller) {
        await new Promise(resolve => navigator.serviceWorker.addEventListener('controllerchange', resolve, { once: true }));
      }
    });
    console.log('Service worker controls the page');
    for (const offline of [false, true]) {
      if (offline) {
        await context.setOffline(true);
        await page.reload();
        await page.waitForFunction(() => globalThis.mentalMathVosk?.Model, null, { timeout: 30000 });
      }
      await page.evaluate(async () => {
        globalThis.offlineTest = { ready: false, detected: false, results: [], errors: [] };
        MentalMathBrowserSpeech.audioContext = new AudioContext({ sampleRate: 48000 });
        await MentalMathBrowserSpeech.audioContext.resume();
        const recognition = globalThis.offlineTestRecognition = new MentalMathOfflineRecognition();
        recognition.onaudiostart = () => { offlineTest.ready = true; };
        recognition.onspeechstart = () => { offlineTest.detected = true; };
        recognition.onerror = event => offlineTest.errors.push(event.message);
        recognition.onresult = event => offlineTest.results.push(event.results[0][0].transcript);
        recognition.start();
      });
      await page.waitForFunction(() => offlineTest.errors.length ||
        ['一', '二', '三', '五', '八'].every(digit => offlineTest.results.includes(digit)),
      null, { timeout: 120000 });
      const state = await page.evaluate(() => offlineTest);
      assert.deepEqual(state.errors, []);
      assert.equal(state.ready, true);
      assert.equal(state.detected, true);
      assert.equal(['一', '二', '三', '五', '八'].every(digit => state.results.includes(digit)), true,
        JSON.stringify(state));
      await page.evaluate(async () => {
        offlineTestRecognition.abort();
        await MentalMathBrowserSpeech.audioContext.close();
      });
      console.log(`PASS: real 48 kHz capture + Vosk Chinese digits, ${offline ? 'offline reload from caches' : 'initial model download'}`);
    }
    assert.deepEqual(errors, []);
    await context.close();
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
