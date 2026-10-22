const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const root = path.join(__dirname, '..');
const script = fs.readFileSync(path.join(root, 'composeApp', 'src', 'wasmJsMain', 'resources', 'speech.js'), 'utf8');
const pack = fs.readFileSync(path.join(root, 'composeApp', 'src', 'androidMain', 'assets', 'tts', 'phrases.pak'));

function environment({ secure = true, packAvailable = true } = {}) {
  const sources = [];
  const recognitions = [];
  const utterances = [];
  const errors = [];
  let trackStopped = false;
  class AudioContext {
    state = 'running';
    destination = {};
    createBuffer(channels, count) {
      return { getChannelData: () => new Float32Array(count) };
    }
    createBufferSource() {
      const source = {
        connect() {},
        disconnect() {},
        start() {},
        stop() {},
        onended: null
      };
      sources.push(source);
      return source;
    }
  }
  class Recognition {
    constructor() { recognitions.push(this); }
    start() {}
    abort() { this.onend?.(); }
  }
  const context = {
    console: { error: (...args) => errors.push(args), warn() {} },
    DataView, Uint8Array, TextDecoder, Map, setTimeout,
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
    context, engine, sources, recognitions, utterances, errors,
    trackStopped: () => trackStopped,
    ready: context.MentalMathBrowserSpeech.loading
  };
}

test('the production voice pack contains all 132 questions and plays PCM', async () => {
  const env = environment();
  await env.ready;
  assert.equal(env.context.MentalMathBrowserSpeech.pack.phrases.size, 132);
  let completed = 0;
  env.engine.speak('三加五', () => completed++);
  assert.equal(env.sources.length, 1);
  assert.equal(env.sources[0].buffer.getChannelData(0).length > 0, true);
  env.sources[0].onended();
  assert.equal(completed, 1);
});

test('stopping a question invalidates an old playback callback', async () => {
  const env = environment();
  await env.ready;
  let completed = false;
  env.engine.speak('三加五', () => { completed = true; });
  const oldCallback = env.sources[0].onended;
  env.engine.stopAll();
  oldCallback();
  assert.equal(completed, false);
});

test('a missing voice pack falls back to system speech with an explicit error', async () => {
  const env = environment({ packAvailable: false });
  await env.ready;
  env.engine.speak('三加五', () => {});
  assert.equal(env.utterances[0].text, '三加五');
  assert.match(String(env.errors[0]), /HTTP 404/);
});

test('recognition preserves alternatives and delivers speech detection once', async () => {
  const env = environment();
  await env.ready;
  let detected = 0;
  let raw = '';
  env.engine.startListening(() => detected++, text => { raw = text; return true; }, assert.fail);
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
  env.engine.startListening(() => { called = true; }, () => { called = true; return true; }, assert.fail);
  const old = env.recognitions[0];
  env.engine.stopAll();
  old.onspeechstart();
  old.onresult({ results: [[{ transcript: '八' }]] });
  assert.equal(called, false);
});

test('answer settlement can stop recognition inside the callback', async () => {
  const env = environment();
  await env.ready;
  env.engine.startListening(() => {}, () => { env.engine.stopAll(); return true; }, assert.fail);
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
