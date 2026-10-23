// Use a local synthetic digit-eight WAV; never use a user's recording.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { chromium } = require(process.env.PLAYWRIGHT_CORE_PATH || 'playwright-core');

(async () => {
  assert.ok(process.env.PWA_TEST_AUDIO_PATH, 'PWA_TEST_AUDIO_PATH must identify a synthetic digit-eight WAV');
  const wav = fs.readFileSync(process.env.PWA_TEST_AUDIO_PATH).toString('base64');
  const browser = await chromium.launch({
    channel: 'chrome', headless: true, args: ['--ignore-certificate-errors']
  });
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: true, serviceWorkers: 'block' });
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    let releasePack;
    const gate = new Promise(resolve => { releasePack = resolve; });
    await context.route('**/phrases.pak', async route => {
      await gate;
      await route.continue();
    });
    await page.goto(process.env.PWA_TEST_URL || 'https://127.0.0.1:8443/');
    await page.waitForFunction(() =>
      !!globalThis.mentalMathVosk && !!globalThis.MentalMathOfflineRecognition &&
      !!globalThis.MentalMathBrowserSpeech);
    await page.evaluate(async wav => {
      await MentalMathOfflineRecognition.preload();
      const engine = globalThis.fastEngine = new MentalMathBrowserSpeech();
      const audio = engine.audioContext();
      const decoded = await audio.decodeAudioData(
        Uint8Array.from(atob(wav), char => char.charCodeAt(0)).buffer
      );
      const samples = decoded.getChannelData(0);
      const first = samples.findIndex(sample => Math.abs(sample) > 0.003);
      let last = samples.length - 1;
      while (last > first && Math.abs(samples[last]) <= 0.003) last--;
      const start = Math.max(0, first - Math.round(decoded.sampleRate * 0.02));
      const end = Math.min(samples.length, last + Math.round(decoded.sampleRate * 0.05));
      const answer = audio.createBuffer(1, end - start, decoded.sampleRate);
      answer.getChannelData(0).set(samples.subarray(start, end));
      const microphone = audio.createMediaStreamDestination();
      window.fastState = { captures: 0, spoken: [], results: [], errors: [], samplesDuringPlayback: [] };
      Object.defineProperty(navigator.mediaDevices, 'getUserMedia', {
        configurable: true,
        value: async () => { fastState.captures++; return microphone.stream; }
      });
      const originalCreate = audio.createBufferSource.bind(audio);
      audio.createBufferSource = () => {
        const source = originalCreate();
        const start = source.start.bind(source);
        source.start = (...args) => {
          if (source.buffer !== answer) {
            fastState.spoken.push({
              frames: source.buffer.length,
              nonzero: source.buffer.getChannelData(0).some(sample => sample !== 0)
            });
          }
          return start(...args);
        };
        return source;
      };
      let round = 0;
      const run = () => {
        engine.setVoiceMode(true);
        engine.speak(round ? '四加四' : '三加五', () => {
          fastState.samplesDuringPlayback.push(engine.preparedRecognition.pending);
          const endedAt = performance.now();
          engine.startListening(() => {
            fastState.readyDelayMs = performance.now() - endedAt;
          }, () => {}, text => {
            fastState.results.push({ text, elapsedMs: performance.now() - endedAt });
            if (++round < 2) setTimeout(run, 100);
            else {
              engine.release();
              fastState.liveTracks = microphone.stream.getTracks()
                .filter(track => track.readyState !== 'ended').length;
              fastState.done = true;
            }
            return true;
          }, error => fastState.errors.push(error));
          const voice = audio.createBufferSource();
          voice.buffer = answer;
          voice.connect(microphone);
          voice.start(audio.currentTime + 0.005);
        }, error => fastState.errors.push(error));
      };
      const button = document.createElement('button');
      button.id = 'fast-test-start';
      button.textContent = '测试快速作答';
      button.style.cssText = 'position:fixed;bottom:0;left:0;z-index:2000';
      button.onclick = run;
      document.body.appendChild(button);
    }, wav);
    await page.locator('#fast-test-start').click();
    await page.waitForTimeout(200);
    assert.equal(await page.evaluate(() => fastState.spoken.length), 0,
      'the first question must wait for phrases.pak');
    releasePack();
    await page.waitForFunction(() => fastState.done || fastState.errors.length,
      null, { timeout: 30000 });
    const result = await page.evaluate(() => fastState);
    assert.deepEqual(result.errors, []);
    assert.equal(result.captures, 1);
    assert.equal(result.spoken.length, 2);
    assert.equal(result.spoken.every(item => item.nonzero && item.frames > 0), true);
    assert.deepEqual(result.samplesDuringPlayback, [0, 0]);
    assert.equal(result.results.length, 2);
    assert.equal(result.results.every(item => item.text.includes('八')), true, JSON.stringify(result));
    assert.ok(result.results.every(item => item.elapsedMs < 1200), JSON.stringify(result));
    assert.ok(result.readyDelayMs < 50, JSON.stringify(result));
    assert.equal(result.liveTracks, 0);
    assert.deepEqual(errors, []);
    console.log('PASS: delayed first PCM playback and immediate digit-eight answers with real Vosk');
    console.log(JSON.stringify(result));
    await context.close();
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
