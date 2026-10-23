const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const root = path.join(__dirname, '..');
const script = fs.readFileSync(path.join(root, 'composeApp', 'src', 'wasmJsMain', 'resources', 'speech.js'), 'utf8');
const pack = fs.readFileSync(path.join(root, 'composeApp', 'src', 'androidMain', 'assets', 'tts', 'phrases.pak'));

function environment({
  secure = true, packAvailable = true, audioSession = true, deferredEnd = false,
  audioState = 'running', resumeError = false, manualTimers = false
} = {}) {
  const sources = [];
  const recognitions = [];
  const utterances = [];
  const errors = [];
  const warnings = [];
  const operations = [];
  const timers = [];
  let trackStopped = false;
  class AudioContext {
    state = audioState;
    currentTime = 0;
    baseLatency = 0;
    outputLatency = 0;
    destination = {};
    async resume() {
      operations.push('resume');
      if (resumeError) throw new Error('Audio resume failed');
      this.state = 'running';
    }
    createBuffer(channels, count, sampleRate) {
      return { duration: count / sampleRate, getChannelData: () => new Float32Array(count) };
    }
    createBufferSource() {
      const source = {
        connect() {},
        disconnect() {},
        start() { operations.push('playback-start'); },
        stop() {},
        onended: null
      };
      sources.push(source);
      return source;
    }
  }
  class Recognition {
    constructor() { recognitions.push(this); }
    start() { operations.push('recognition-start'); }
    abort() {
      operations.push('recognition-abort');
      if (!deferredEnd) this.onend?.();
    }
  }
  const context = {
    console: {
      error: (...args) => errors.push(args),
      warn: (...args) => warnings.push(args)
    },
    DataView, Uint8Array, TextDecoder, Map,
    performance: { now: () => 1000 },
    setTimeout: manualTimers ? (callback, delay) => {
      const timer = { callback, delay, cancelled: false };
      timers.push(timer);
      return timer;
    } : setTimeout,
    clearTimeout: manualTimers ? timer => { timer.cancelled = true; } : clearTimeout,
    fetch: async () => ({
      ok: packAvailable,
      status: packAvailable ? 200 : 404,
      arrayBuffer: async () => pack.buffer.slice(pack.byteOffset, pack.byteOffset + pack.byteLength)
    }),
    navigator: {
      mediaDevices: {
        getUserMedia: async () => ({
          getTracks: () => [{ stop() { trackStopped = true; } }]
        })
      }
    },
    SpeechSynthesisUtterance: class {
      constructor(text) { this.text = text; }
    }
  };
  if (audioSession) {
    let type = 'auto';
    context.navigator.audioSession = {
      get type() { return type; },
      set type(value) {
        type = value;
        operations.push('session:' + value);
      }
    };
  }
  context.window = {
    isSecureContext: secure,
    AudioContext,
    SpeechRecognition: Recognition,
    speechSynthesis: {
      cancel() {},
      speak(utterance) { utterances.push(utterance); }
    }
  };
  vm.createContext(context);
  vm.runInContext(script, context);
  const engine = new context.MentalMathBrowserSpeech();
  return {
    context, engine, sources, recognitions, utterances, errors, warnings, operations, timers,
    audioContext: () => context.MentalMathBrowserSpeech.audioContext,
    trackStopped: () => trackStopped,
    ready: context.MentalMathBrowserSpeech.loading
  };
}

test('the production voice pack contains all 132 questions and plays PCM', async () => {
  const env = environment();
  await env.ready;
  assert.equal(env.context.MentalMathBrowserSpeech.pack.phrases.size, 132);
  let completed = 0;
  await env.engine.speak('三加五', () => completed++, assert.fail);
  assert.equal(env.sources.length, 1);
  assert.equal(env.sources[0].buffer.getChannelData(0).length > 0, true);
  env.audioContext().currentTime = env.sources[0].buffer.duration;
  env.sources[0].onended();
  assert.equal(completed, 1);
});

test('stopping a question invalidates an old playback callback', async () => {
  const env = environment();
  await env.ready;
  let completed = false;
  await env.engine.speak('三加五', () => { completed = true; }, assert.fail);
  const oldCallback = env.sources[0].onended;
  env.engine.stopAll();
  oldCallback();
  assert.equal(completed, false);
});

test('a missing voice pack falls back to system speech with an explicit error', async () => {
  const env = environment({ packAvailable: false });
  await env.ready;
  await env.engine.speak('三加五', () => {}, assert.fail);
  assert.equal(env.utterances[0].text, '三加五');
  assert.match(String(env.errors[0]), /HTTP 404/);
});

test('recognition preserves alternatives and delivers speech detection once', async () => {
  const env = environment();
  await env.ready;
  let detected = 0;
  let raw = '';
  await env.engine.startListening(() => {}, () => detected++, text => { raw = text; return true; }, assert.fail);
  const recognition = env.recognitions[0];
  recognition.onspeechstart();
  recognition.onresult({
    results: { length: 1, 0: { length: 2, 0: { transcript: '八' }, 1: { transcript: '吧' } } }
  });
  assert.equal(detected, 1);
  assert.equal(raw, '八\n吧');
  assert.equal(env.engine.recognition, null);
});

test('callbacks from a stopped recognition do not reach the next question', async () => {
  const env = environment();
  await env.ready;
  let called = false;
  await env.engine.startListening(() => {}, () => { called = true; }, () => { called = true; return true; }, assert.fail);
  const old = env.recognitions[0];
  env.engine.stopAll();
  old.onspeechstart();
  old.onresult({ results: [[{ transcript: '八' }]] });
  assert.equal(called, false);
});

test('answer settlement can stop recognition inside the callback', async () => {
  const env = environment();
  await env.ready;
  await env.engine.startListening(() => {}, () => {}, () => { env.engine.stopAll(); return true; }, assert.fail);
  env.recognitions[0].onresult({ results: [[{ transcript: '八' }]] });
  assert.equal(env.engine.recognition, null);
});

test('LAN HTTP rejects voice permission so the app can select keypad mode', async () => {
  const env = environment({ secure: false });
  await env.ready;
  let granted;
  env.engine.requestRecordPermission(value => { granted = value; });
  assert.equal(granted, false);
});

test('permission acquisition releases the temporary microphone stream', async () => {
  const env = environment();
  await env.ready;
  const granted = await new Promise(resolve => env.engine.requestRecordPermission(resolve));
  assert.equal(granted, true);
  assert.equal(env.engine.hasRecordPermission(), true);
  assert.equal(env.trackStopped(), true);
});

test('listening readiness waits for capture and fires only once', async () => {
  const env = environment();
  await env.ready;
  let ready = 0;
  await env.engine.startListening(() => ready++, () => {}, () => false, assert.fail);
  assert.equal(ready, 0);
  const recognition = env.recognitions[0];
  recognition.onaudiostart();
  recognition.onaudiostart();
  assert.equal(ready, 1);
  env.engine.stopAll();
  recognition.onaudiostart();
  assert.equal(ready, 1);
});

test('permission errors are explicit and do not deliver stale readiness', async () => {
  const env = environment();
  await env.ready;
  let ready = 0;
  const reasons = [];
  await env.engine.startListening(() => ready++, () => {}, () => false, reason => reasons.push(reason));
  const recognition = env.recognitions[0];
  recognition.onerror({ error: 'not-allowed' });
  recognition.onaudiostart();
  assert.equal(ready, 0);
  assert.equal(reasons.length, 1);
  assert.match(reasons[0], /权限/);
  assert.equal(env.engine.recognition, null);
});

test('playback waits for recognition end rather than treating abort as microphone release', async () => {
  const env = environment({ deferredEnd: true });
  await env.ready;
  await env.engine.startListening(() => {}, () => {}, () => false, assert.fail);
  const recognition = env.recognitions[0];
  env.engine.stopAll();
  const playback = env.engine.speak('三加五', () => {}, assert.fail);
  await Promise.resolve();
  assert.equal(env.sources.length, 0);
  assert.equal(env.operations.includes('session:playback'), false);
  recognition.onend();
  await playback;
  assert.equal(env.sources.length, 1);
  assert.ok(env.operations.indexOf('recognition-abort') <
    env.operations.indexOf('session:playback'));
  assert.ok(env.operations.indexOf('session:playback') <
    env.operations.indexOf('playback-start'));
});

test('exiting while waiting for microphone release cannot start stale playback', async () => {
  const env = environment({ deferredEnd: true });
  await env.ready;
  await env.engine.startListening(() => {}, () => {}, () => false, assert.fail);
  const playback = env.engine.speak('三加五', assert.fail, assert.fail);
  env.engine.stopAll();
  env.recognitions[0].onend();
  await playback;
  assert.equal(env.sources.length, 0);
  assert.equal(env.context.navigator.audioSession.type, 'auto');
});

test('a replacement recognition waits for release and supersedes an earlier pending request', async () => {
  const env = environment({ deferredEnd: true });
  await env.ready;
  await env.engine.startListening(() => {}, () => {}, () => false, assert.fail);
  const first = env.engine.startListening(assert.fail, assert.fail, assert.fail, assert.fail);
  const second = env.engine.startListening(() => {}, () => {}, () => false, assert.fail);
  assert.equal(env.recognitions.length, 1);
  env.recognitions[0].onend();
  await Promise.all([first, second]);
  assert.equal(env.recognitions.length, 2);
  assert.equal(env.engine.recognition, env.recognitions[1]);
});

test('microphone release timeout reports a playback error instead of playing over active capture', async () => {
  const env = environment({ deferredEnd: true, manualTimers: true });
  await env.ready;
  await env.engine.startListening(() => {}, () => {}, () => false, assert.fail);
  const errors = [];
  const playback = env.engine.speak('三加五', assert.fail, error => errors.push(error));
  assert.equal(env.timers.length, 1);
  assert.equal(env.timers[0].delay, 3000);
  env.timers[0].callback();
  await playback;
  assert.equal(errors.length, 1);
  assert.match(String(env.errors[0]), /Microphone release timed out/);
  assert.equal(env.sources.length, 0);
  env.recognitions[0].onend();
});

test('a late timeout after exit cannot report an error to the old question', async () => {
  const env = environment({ deferredEnd: true, manualTimers: true });
  await env.ready;
  await env.engine.startListening(() => {}, () => {}, () => false, assert.fail);
  const playback = env.engine.speak('三加五', assert.fail, assert.fail);
  env.engine.stopAll();
  env.timers[0].callback();
  await playback;
  assert.equal(env.sources.length, 0);
  env.recognitions[0].onend();
});

test('capture starts in a recording session only after audible playback completion', async () => {
  const env = environment({ manualTimers: true });
  await env.ready;
  let listening;
  await env.engine.speak('三加五', () => {
    listening = env.engine.startListening(() => {}, () => {}, () => false, assert.fail);
  }, assert.fail);
  const context = env.audioContext();
  const duration = env.sources[0].buffer.duration;
  context.currentTime = duration;
  context.getOutputTimestamp = () => ({ contextTime: duration - 0.2, performanceTime: 1000 });
  env.sources[0].onended();
  assert.equal(env.context.navigator.audioSession.type, 'playback');
  assert.equal(env.recognitions.length, 0);
  assert.ok(Math.abs(env.timers[0].delay - 200) < 0.01);
  env.timers[0].callback();
  await listening;
  assert.equal(env.context.navigator.audioSession.type, 'play-and-record');
  assert.equal(env.recognitions.length, 1);
});

test('output latency provides a measured drain delay without timestamp support', async () => {
  const env = environment({ manualTimers: true });
  await env.ready;
  let done = false;
  await env.engine.speak('三加五', () => { done = true; }, assert.fail);
  const context = env.audioContext();
  context.currentTime = env.sources[0].buffer.duration;
  context.baseLatency = 0.01;
  context.outputLatency = 0.08;
  env.sources[0].onended();
  assert.equal(done, false);
  assert.ok(Math.abs(env.timers[0].delay - 90) < 0.01);
  env.timers[0].callback();
  assert.equal(done, true);
});

test('exiting while audio drains cannot start listening for the old question', async () => {
  const env = environment({ manualTimers: true });
  await env.ready;
  await env.engine.speak('三加五', assert.fail, assert.fail);
  const context = env.audioContext();
  context.currentTime = env.sources[0].buffer.duration;
  context.outputLatency = 0.08;
  env.sources[0].onended();
  env.engine.stopAll();
  env.timers[0].callback();
  assert.equal(env.context.navigator.audioSession.type, 'auto');
});

test('suspended audio resumes before PCM playback starts', async () => {
  const env = environment({ audioState: 'suspended' });
  await env.ready;
  await env.engine.speak('三加五', () => {}, assert.fail);
  assert.ok(env.operations.indexOf('resume') < env.operations.indexOf('playback-start'));
});

test('audio resume failures report an error without starting capture or countdown', async () => {
  const env = environment({ audioState: 'suspended', resumeError: true });
  await env.ready;
  const errors = [];
  await env.engine.speak('三加五', assert.fail, error => errors.push(error));
  assert.equal(errors.length, 1);
  assert.equal(env.sources.length, 0);
  assert.equal(env.context.navigator.audioSession.type, 'auto');
});

test('browsers without the experimental Audio Session API retain playback and recognition', async () => {
  const env = environment({ audioSession: false });
  await env.ready;
  await env.engine.speak('三加五', () => {}, assert.fail);
  assert.equal(env.sources.length, 1);
  await env.engine.startListening(() => {}, () => {}, () => false, assert.fail);
  assert.equal(env.recognitions.length, 1);
  assert.deepEqual(env.errors, []);
});

test('permission priming restores automatic audio routing after releasing its stream', async () => {
  const env = environment();
  await env.ready;
  await new Promise(resolve => env.engine.requestRecordPermission(resolve));
  assert.equal(env.trackStopped(), true);
  assert.ok(env.operations.includes('session:play-and-record'));
  assert.equal(env.context.navigator.audioSession.type, 'auto');
});
