const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const resources = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const source = fs.readFileSync(path.join(resources, 'offline-speech.js'), 'utf8');

function environment({ microphone, acceptError = false, removeError = false, worklet } = {}) {
  const models = [], recognizers = [], nodes = [], timers = [], errors = [];
  let stoppedTracks = 0, captures = 0, workletLoads = 0;
  class Emitter {
    handlers = {};
    on(type, handler) { this.handlers[type] = handler; }
    emit(type, detail) { this.handlers[type]?.(detail); }
  }
  class Recognizer extends Emitter {
    constructor(sampleRate, grammar) {
      super();
      this.sampleRate = sampleRate;
      this.grammar = JSON.parse(grammar);
      this.samples = [];
      recognizers.push(this);
    }
    acceptWaveformFloat(samples, sampleRate) {
      if (acceptError) throw new Error('worker disconnected');
      this.samples.push({ samples, sampleRate });
    }
    remove() {
      if (removeError) throw new Error('worker cleanup failed');
      this.removed = true;
    }
  }
  class Model extends Emitter {
    constructor(url) { super(); this.url = url; models.push(this); }
    KaldiRecognizer = Recognizer;
    terminate() { this.terminated = true; }
  }
  class Worklet {
    constructor() { nodes.push(this); }
    port = { close() { this.closed = true; } };
    connect() {}
    disconnect() { this.disconnected = true; }
  }
  const track = { stop() { stoppedTracks++; } };
  const stream = { getTracks: () => [track], getAudioTracks: () => [track] };
  const audioContext = {
    sampleRate: 48000, currentTime: 0, destination: {},
    audioWorklet: { addModule: async () => {
      workletLoads++;
      if (worklet) await worklet();
    } },
    createMediaStreamSource: () => ({ connect() {}, disconnect() {} })
  };
  const context = {
    EventTarget, Event, URL, Float32Array, Set, Promise, Math,
    document: { baseURI: 'https://localhost/MentalMath/' },
    window: {},
    mentalMathVosk: { Model },
    MentalMathBrowserSpeech: { audioContext },
    AudioWorkletNode: Worklet,
    navigator: { mediaDevices: { getUserMedia: async () => {
      captures++;
      return microphone ? microphone(stream) : stream;
    } } },
    console: { error: (...args) => errors.push(args) },
    setTimeout: callback => { const timer = { callback }; timers.push(timer); return timer; },
    clearTimeout: timer => { timer.cancelled = true; }
  };
  vm.runInNewContext(source, context);
  return {
    context, models, recognizers, nodes, timers, errors, track,
    stoppedTracks: () => stoppedTracks, captures: () => captures,
    workletLoads: () => workletLoads,
    preload: () => context.MentalMathOfflineRecognition.preload(),
    stream,
    create: () => new context.MentalMathOfflineRecognition(),
    async start() {
      const recognition = this.create();
      const starting = recognition.start();
      if (models.length === 1 && !models[0].loaded) {
        models[0].loaded = true;
        models[0].emit('load', { result: true });
      }
      await starting;
      return recognition;
    },
    audio(samples = new Float32Array(1024), startTime = audioContext.currentTime) {
      nodes.at(-1).port.onmessage({ data: { samples, startTime } });
    }
  };
}

test('offline model uses the Pages subpath, 0-10 grammar and actual capture readiness', async () => {
  const env = environment();
  const recognition = await env.start();
  assert.equal(env.models[0].url, 'https://localhost/MentalMath/vosk-model-cn.tar.gz');
  assert.deepEqual(env.recognizers[0].grammar, ['零', '一', '二', '三', '四', '五', '六', '七', '八', '九', '十', '两', '[unk]']);
  assert.equal(recognition.ready, false);
  let ready = 0;
  recognition.onaudiostart = () => ready++;
  env.audio(); env.audio();
  assert.equal(ready, 1);
  assert.equal(env.recognizers[0].samples[0].sampleRate, 48000);
});

test('one model is reused across questions and each microphone is released', async () => {
  const env = environment();
  const first = await env.start();
  first.abort(); first.abort();
  const second = await env.start();
  assert.equal(env.models.length, 1);
  assert.equal(env.workletLoads(), 1);
  assert.equal(env.recognizers.length, 2);
  assert.equal(env.stoppedTracks(), 1);
  assert.equal(env.nodes[0].port.closed, true);
  assert.equal(env.recognizers[0].removed, true);
  second.abort();
});

async function prepareQuestion(env) {
  const recognition = env.create();
  const preparing = recognition.prepare(env.stream);
  if (!env.models[0].loaded) {
    env.models[0].loaded = true;
    env.models[0].emit('load', { result: true });
  }
  while (!recognition.node) await Promise.resolve();
  env.audio(new Float32Array(1024).fill(0.5));
  await preparing;
  return recognition;
}

test('offline capture is warm before playback but never decodes the spoken question', async () => {
  const env = environment();
  const recognition = await prepareQuestion(env);
  assert.equal(env.captures(), 0, 'the session supplies its already-open stream');
  assert.equal(env.recognizers[0].samples.length, 0);
  env.audio(new Float32Array(1024).fill(0.5));
  assert.equal(env.recognizers[0].samples.length, 0);
  let ready = 0, detected = 0, result = '';
  recognition.onaudiostart = () => ready++;
  recognition.onspeechstart = () => detected++;
  recognition.onresult = event => { result = event.results[0][0].transcript; };
  await recognition.start();
  assert.equal(ready, 1, 'warm capture activates without reopening the microphone');
  env.audio(new Float32Array(1024).fill(0.02));
  env.recognizers[0].emit('partialresult', { result: { partial: '八' } });
  assert.equal(detected, 1);
  assert.equal(result, '八');
  recognition.abort();
  assert.equal(env.stoppedTracks(), 0, 'question cleanup must preserve the round stream');
  assert.equal(env.recognizers[0].removed, true);
  assert.equal(env.nodes[0].port.closed, true);
});

test('delayed microphone packets discard playback samples and preserve the immediate answer tail', async () => {
  const env = environment();
  const recognition = await prepareQuestion(env);
  env.context.MentalMathBrowserSpeech.audioContext.currentTime = 1;
  await recognition.start();
  env.audio(new Float32Array(480).fill(0.5), 0.98);
  assert.equal(env.recognizers[0].samples.length, 0);
  const boundary = new Float32Array(960);
  boundary.fill(0.5, 0, 480);
  boundary.fill(0.02, 480);
  env.audio(boundary, 0.99);
  const accepted = env.recognizers[0].samples[0].samples;
  assert.ok(accepted.length >= 479 && accepted.length <= 480);
  assert.equal(accepted.every(sample => sample < 0.03), true);
  recognition.abort();
});

test('cancelling prepared capture settles its wait without stopping the session stream', async () => {
  const env = environment();
  const recognition = env.create();
  const preparing = recognition.prepare(env.stream);
  env.models[0].emit('load', { result: true });
  while (!recognition.node) await Promise.resolve();
  recognition.abort();
  await assert.rejects(preparing, /取消/);
  assert.equal(env.stoppedTracks(), 0);
});

test('prepared capture startup failure is explicit rather than silently starting playback', async () => {
  const env = environment();
  const recognition = env.create();
  const preparing = recognition.prepare(env.stream);
  env.models[0].emit('load', { result: true });
  while (!recognition.node) await Promise.resolve();
  while (env.timers.length < 2) await Promise.resolve();
  env.timers[1].callback();
  await assert.rejects(preparing, /收音准备超时/);
  assert.equal(recognition.stopped, true);
});

test('homepage preload shares the pending model with practice without opening a microphone', async () => {
  const env = environment();
  const preparing = env.preload();
  assert.equal(env.preload(), preparing);
  assert.equal(env.models.length, 1);
  assert.equal(env.captures(), 0);
  assert.equal(env.workletLoads(), 0);
  const recognition = env.create();
  const starting = recognition.start();
  env.models[0].emit('load', { result: true });
  await Promise.all([preparing, starting]);
  assert.equal(env.models.length, 1);
  recognition.abort();
  const next = await env.start();
  assert.equal(env.models.length, 1);
  assert.equal(env.models[0].terminated, undefined);
  assert.equal(env.workletLoads(), 1);
  next.abort();
});

test('preparation observers receive loading, ready, runtime error and retry states', async () => {
  const env = environment();
  const states = [];
  const stop = env.context.MentalMathOfflineRecognition.observePreparation(state => states.push(state));
  const preparing = env.preload();
  env.models[0].emit('load', { result: true });
  await preparing;
  const late = [];
  env.context.MentalMathOfflineRecognition.observePreparation(state => late.push(state.status));
  assert.deepEqual(late, ['ready']);
  env.models[0].emit('error', { error: 'worker stopped' });
  assert.deepEqual(states.map(state => state.status), ['idle', 'loading', 'ready', 'error']);
  assert.equal(states.at(-1).message, 'worker stopped');
  const retry = env.preload();
  assert.equal(states.at(-1).status, 'loading');
  env.models[1].emit('load', { result: true });
  await retry;
  stop();
  env.models[1].emit('error', { error: 'stopped again' });
  assert.equal(states.at(-1).status, 'ready');
});

test('failed homepage preload can be retried when entering practice', async () => {
  const env = environment();
  const preparing = env.preload();
  env.models[0].emit('error', { error: 'download failed' });
  await assert.rejects(preparing, /download failed/);
  const recognition = env.create();
  const starting = recognition.start();
  assert.equal(env.models.length, 2);
  env.models[1].emit('load', { result: true });
  await starting;
  assert.equal(env.captures(), 1);
  recognition.abort();
});

test('a failed worklet load is retried instead of caching the failure', async () => {
  let attempts = 0;
  const env = environment({ worklet: async () => {
    if (++attempts === 1) throw new Error('worklet unavailable');
  } });
  const first = await env.start();
  assert.equal(first.stopped, true);
  assert.equal(env.captures(), 0);
  const second = await env.start();
  assert.equal(second.stopped, false);
  assert.equal(env.workletLoads(), 2);
  second.abort();
});

test('replacing the audio context loads its own worklet module', async () => {
  const env = environment();
  const first = await env.start();
  first.abort();
  env.context.MentalMathBrowserSpeech.audioContext = {
    ...env.context.MentalMathBrowserSpeech.audioContext
  };
  const second = await env.start();
  assert.equal(env.workletLoads(), 2);
  second.abort();
});

test('cancelling model preparation never opens the microphone', async () => {
  const env = environment();
  const recognition = env.create();
  const starting = recognition.start();
  recognition.abort();
  env.models[0].emit('load', { result: true });
  await starting;
  assert.equal(env.captures(), 0);
});

test('a microphone granted after cancellation is immediately released', async () => {
  let grant;
  const env = environment({ microphone: stream => new Promise(resolve => { grant = () => resolve(stream); }) });
  const recognition = env.create();
  const starting = recognition.start();
  env.models[0].emit('load', { result: true });
  while (!grant) await Promise.resolve();
  recognition.abort();
  grant();
  await starting;
  assert.equal(env.stoppedTracks(), 1);
  assert.equal(env.recognizers.length, 0);
});

for (const failure of ['error', 'timeout', 'invalid-load']) {
  test(`model ${failure} is explicit, permits retry and ignores late events`, async () => {
    const env = environment();
    const first = env.create();
    let error;
    first.onerror = event => { error = event; };
    const starting = first.start();
    if (failure === 'timeout') env.timers[0].callback();
    else env.models[0].emit(failure === 'error' ? 'error' : 'load', { result: false });
    await starting;
    assert.equal(error.error, 'offline-unavailable');
    assert.equal(env.captures(), 0);
    const second = env.create();
    const retry = second.start();
    assert.equal(env.models.length, 2);
    env.models[0].emit('load', { result: true });
    env.models[0].emit('error', { error: 'late failure' });
    env.models[1].emit('load', { result: true });
    await retry;
    assert.equal(second.stopped, false);
    assert.equal(env.models[1].terminated, undefined);
    second.abort();
  });
}

test('runtime model errors end active recognition and release capture', async () => {
  const env = environment();
  const recognition = await env.start();
  let failed = 0;
  recognition.onerror = () => failed++;
  env.models[0].emit('error', { error: 'runtime failure' });
  assert.equal(failed, 1);
  assert.equal(recognition.stopped, true);
  assert.equal(env.stoppedTracks(), 1);
});

test('interim/final results remove word spaces, ignore unknowns and acknowledge queued audio', async () => {
  const env = environment();
  const recognition = await env.start();
  const results = [];
  recognition.onresult = event => results.push(event.results[0]);
  env.audio(); env.audio();
  env.recognizers[0].emit('partialresult', { result: { partial: '[unk]' } });
  env.recognizers[0].emit('partialresult', { result: { partial: '十 ' } });
  env.recognizers[0].emit('result', { result: { text: '一 十' } });
  assert.equal(results.length, 2);
  assert.equal(results[0][0].transcript, '十');
  assert.equal(results[0].isFinal, false);
  assert.equal(results[1][0].transcript, '一十');
  assert.equal(results[1].isFinal, true);
  assert.equal(recognition.pending, 0);
});

test('energy detects speech once; silence does not lock the countdown', async () => {
  const env = environment();
  const recognition = await env.start();
  let detected = 0;
  recognition.onspeechstart = () => detected++;
  env.audio();
  assert.equal(detected, 0);
  env.audio(new Float32Array(1024).fill(0.02));
  env.audio(new Float32Array(1024).fill(0.02));
  assert.equal(detected, 1);
});

test('synchronous cancellation in readiness cannot feed a removed recognizer', async () => {
  const env = environment();
  const recognition = await env.start();
  recognition.onaudiostart = () => recognition.abort();
  env.audio();
  assert.equal(env.recognizers[0].samples.length, 0);
});

test('worker transfer errors fail explicitly and release the microphone', async () => {
  const env = environment({ acceptError: true });
  const recognition = await env.start();
  let error;
  recognition.onerror = event => { error = event; };
  env.audio();
  assert.match(error.message, /worker disconnected/);
  assert.equal(env.stoppedTracks(), 1);
});

test('failed worker cleanup is logged without blocking microphone release or the end event', async () => {
  const env = environment({ removeError: true });
  const recognition = await env.start();
  let ended = false;
  recognition.onend = () => { ended = true; };
  recognition.abort();
  assert.equal(ended, true);
  assert.equal(env.stoppedTracks(), 1);
  assert.equal(env.errors.length, 1);
});

test('worklet errors and disconnected microphones end recognition', async () => {
  for (const type of ['processor', 'track']) {
    const env = environment();
    const recognition = await env.start();
    if (type === 'processor') env.nodes[0].onprocessorerror();
    else env.track.onended();
    assert.equal(recognition.stopped, true);
    assert.equal(env.stoppedTracks(), 1);
  }
});

test('unprocessed audio is bounded to two seconds instead of accumulating latency', async () => {
  const env = environment();
  const recognition = await env.start();
  for (let i = 0; i < 100 && !recognition.stopped; i++) env.audio();
  assert.equal(recognition.stopped, true);
  assert.equal(env.recognizers[0].samples.length, Math.ceil(48000 * 2 / 1024));
});

test('capture batches 1024 samples without routing microphone input to output', () => {
  let Processor;
  const messages = [];
  const context = {
    Float32Array,
    currentFrame: 0, sampleRate: 48000,
    AudioWorkletProcessor: class { port = { postMessage: data => messages.push(data) }; },
    registerProcessor: (name, processor) => { assert.equal(name, 'mentalmath-capture'); Processor = processor; }
  };
  vm.runInNewContext(fs.readFileSync(path.join(resources, 'offline-capture.js'), 'utf8'), context);
  const processor = new Processor();
  const output = new Float32Array(128);
  for (let i = 0; i < 17; i++) {
    context.currentFrame = i * 128;
    processor.process([[new Float32Array(128).fill(0.5)]], [[output]]);
  }
  assert.equal(messages.length, 2);
  assert.equal(messages[0].samples.length, 1024);
  assert.equal(messages[0].samples[0], 0.5);
  assert.equal(messages[0].startTime, 0);
  assert.equal(messages[1].startTime, 1024 / 48000);
  assert.equal(output.every(sample => sample === 0), true);
});
