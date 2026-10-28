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
  audioState = 'running', resumeError = false, manualTimers = false,
  search = '?speechEngine=browser', offlinePreload, packGate, prepareGate, microphone, resumeGate
} = {}) {
  const sources = [];
  const recognitions = [];
  const oscillators = [];
  const utterances = [];
  const errors = [];
  const warnings = [];
  const operations = [];
  const timers = [];
  let trackStopped = false;
  const track = {
    readyState: 'live',
    stop() { trackStopped = true; this.readyState = 'ended'; }
  };
  const stream = { getTracks: () => [track], getAudioTracks: () => [track] };
  class AudioContext {
    state = audioState;
    currentTime = 0;
    baseLatency = 0;
    outputLatency = 0;
    destination = {};
    async suspend() {
      operations.push('suspend-start');
      this.state = 'suspended';
      operations.push('suspend-done');
    }
    async resume() {
      operations.push('resume');
      if (resumeGate) await resumeGate;
      if (resumeError) throw new Error('Audio resume failed');
      this.state = 'running';
    }
    async close() {
      this.state = 'closed';
      operations.push('context-close');
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
    createOscillator() {
      const osc = {
        type: 'sine',
        frequency: {
          setValueAtTime() {},
          exponentialRampToValueAtTime() {}
        },
        connect() {},
        start() { operations.push('oscillator-start'); },
        stop() { operations.push('oscillator-stop'); }
      };
      oscillators.push(osc);
      return osc;
    }
    createGain() {
      return {
        gain: {
          setValueAtTime() {},
          exponentialRampToValueAtTime() {}
        },
        connect() {}
      };
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
    URLSearchParams,
    performance: { now: () => 1000 },
    setTimeout: manualTimers ? (callback, delay) => {
      const timer = { callback, delay, cancelled: false };
      timers.push(timer);
      return timer;
    } : (callback, delay) => setTimeout(callback, delay).unref(),
    clearTimeout: manualTimers ? timer => { if (timer) timer.cancelled = true; } : clearTimeout,
    fetch: async () => {
      if (packGate) await packGate;
      return ({
      ok: packAvailable,
      status: packAvailable ? 200 : 404,
      arrayBuffer: async () => pack.buffer.slice(pack.byteOffset, pack.byteOffset + pack.byteLength)
      });
    },
    navigator: {
      mediaDevices: {
        getUserMedia: async () => {
          operations.push('microphone-open');
          return microphone ? microphone(stream) : stream;
        }
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
    location: { search },
    isSecureContext: secure,
    AudioContext,
    SpeechRecognition: Recognition,
    speechSynthesis: {
      cancel() {},
      speak(utterance) { utterances.push(utterance); }
    }
  };
  context.document = new EventTarget();
  context.document.visibilityState = 'visible';
  context.window.addEventListener = (...args) => context.document.addEventListener(...args);
  if (offlinePreload) {
    context.MentalMathOfflineRecognition = class {
      static preload = offlinePreload;
    };
  }
  if (prepareGate) {
    context.MentalMathOfflineRecognition = class extends Recognition {
      async prepare(stream) {
        this.stream = stream;
        operations.push('capture-prepare');
        await prepareGate;
      }
    };
  }
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

test('offline model preparation begins on the homepage without audio or microphone access', async () => {
  let preloads = 0;
  const env = environment({ search: '', offlinePreload: async () => { preloads++; } });
  await env.ready;
  assert.equal(preloads, 1);
  assert.equal(env.audioContext(), null);
  assert.equal(env.trackStopped(), false);
  assert.equal(env.recognitions.length, 0);
});

test('gesture recovery resumes before deferred navigation without starting playback or capture', async () => {
  const env = environment({ audioState: 'suspended', manualTimers: true });
  await env.ready;
  env.engine.prepareFromGesture();
  assert.ok(env.operations.includes('resume'));
  assert.equal(env.sources.length, 0);
  assert.equal(env.operations.includes('microphone-open'), false);
  await env.engine.speak('三加五', () => {}, assert.fail);
  assert.equal(env.sources.length, 1);
  assert.equal(env.operations.filter(item => item === 'resume').length, 1);
  env.engine.release();
});

test('background pause releases capture and replaces context without reloading the voice pack', async () => {
  const env = environment({ manualTimers: true, prepareGate: Promise.resolve(), search: '' });
  await env.ready;
  env.engine.setVoiceMode(true);
  await env.engine.speak('三加五', assert.fail, assert.fail);
  const old = env.audioContext();
  const pack = env.context.MentalMathBrowserSpeech.pack;
  let pauses = 0;
  env.engine.setPauseListener(() => pauses++);
  const ended = env.sources[0].onended;
  env.context.document.visibilityState = 'hidden';
  env.context.document.dispatchEvent(new Event('visibilitychange'));
  assert.equal(pauses, 1);
  assert.equal(env.trackStopped(), true);
  assert.equal(old.state, 'closed');
  assert.equal(env.audioContext(), null);
  ended();
  env.engine.prepareFromGesture();
  assert.notEqual(env.audioContext(), old);
  assert.equal(env.context.MentalMathBrowserSpeech.pack, pack);
  env.engine.release();
});

test('hung audio resume reports a bounded error and discards the broken context', async () => {
  let resume;
  const env = environment({
    audioState: 'suspended', manualTimers: true,
    resumeGate: new Promise(resolve => { resume = resolve; })
  });
  await env.ready;
  const failures = [];
  env.engine.prepareFromGesture();
  const old = env.audioContext();
  const playback = env.engine.speak('三加五', assert.fail, error => failures.push(error));
  env.timers.find(timer => timer.delay === 5000).callback();
  await playback;
  assert.equal(failures.length, 1);
  assert.match(failures[0], /音频恢复超时/);
  assert.equal(env.audioContext(), null);
  assert.equal(env.sources.length, 0);
  resume();
  await Promise.resolve();
  assert.notEqual(env.audioContext(), old);
  env.engine.prepareFromGesture();
  await env.engine.speak('三加五', () => {}, assert.fail);
  assert.equal(env.sources.length, 1);
  assert.notEqual(env.audioContext(), old);
  env.engine.release();
});

test('closed audio contexts are replaced before a new gesture resumes playback', async () => {
  const env = environment({ manualTimers: true });
  await env.ready;
  const old = env.engine.audioContext();
  await old.close();
  env.engine.prepareFromGesture();
  await env.engine.speak('三加五', () => {}, assert.fail);
  assert.notEqual(env.audioContext(), old);
  assert.equal(env.sources.length, 1);
  env.engine.release();
});

test('hung capture module preparation times out and cannot play after cancellation', async () => {
  let prepared;
  const env = environment({
    manualTimers: true, search: '',
    prepareGate: new Promise(resolve => { prepared = resolve; })
  });
  await env.ready;
  env.engine.setVoiceMode(true);
  const failures = [];
  const playback = env.engine.speak('三加五', assert.fail, error => failures.push(error));
  while (!env.timers.some(timer => timer.delay === 100000)) await Promise.resolve();
  env.timers.find(timer => timer.delay === 100000).callback();
  await playback;
  assert.equal(failures.length, 1);
  assert.equal(env.trackStopped(), true);
  prepared();
  await Promise.resolve();
  assert.equal(env.sources.length, 0);
});

test('microphone timeout releases a late permission stream without starting stale playback', async () => {
  let grant;
  const env = environment({
    manualTimers: true, prepareGate: Promise.resolve(), search: '',
    microphone: stream => new Promise(resolve => { grant = () => resolve(stream); })
  });
  await env.ready;
  env.engine.setVoiceMode(true);
  const failures = [];
  const playback = env.engine.speak('三加五', assert.fail, error => failures.push(error));
  while (!grant) await Promise.resolve();
  env.timers.find(timer => timer.delay === 15000 && !timer.cancelled).callback();
  await playback;
  assert.equal(failures.length, 1);
  grant();
  await Promise.resolve();
  await Promise.resolve();
  assert.equal(env.trackStopped(), true);
  assert.equal(env.sources.length, 0);
});

test('missing playback completion reports an error instead of hanging the question', async () => {
  const env = environment({ manualTimers: true });
  await env.ready;
  const failures = [];
  await env.engine.speak('三加五', assert.fail, error => failures.push(error));
  env.timers.find(timer => !timer.cancelled && timer.delay > 5000 && timer.delay < 15000).callback();
  assert.equal(failures.length, 1);
  assert.equal(env.audioContext(), null);
});

test('homepage preload errors are logged without blocking the app', async () => {
  const env = environment({ search: '', offlinePreload: async () => { throw new Error('model unavailable'); } });
  await env.ready;
  assert.match(String(env.errors[0]), /model unavailable/);
  assert.equal(env.audioContext(), null);
});

test('system recognition and insecure pages do not preload the offline model', async () => {
  let preloads = 0;
  const offlinePreload = async () => { preloads++; };
  await environment({ offlinePreload }).ready;
  await environment({ search: '', secure: false, offlinePreload }).ready;
  assert.equal(preloads, 0);
});

test('the production voice pack contains all 132 questions and plays PCM', async () => {
  const env = environment();
  await env.ready;
  assert.equal(env.context.MentalMathBrowserSpeech.pack.phrases.size, 132);
  let completed = 0;
  await env.engine.speak('三加五', () => completed++, assert.fail);
  assert.equal(env.sources.length, 1);
  assert.equal(env.sources[0].buffer.getChannelData(0).length > 0, true);
  env.audioContext().currentTime = env.sources[0].buffer.duration;
  await env.sources[0].onended();
  assert.equal(completed, 1);
});

test('questions with milestone prefix extract math expression and play PCM without timeout', async () => {
  const env = environment();
  await env.ready;
  let completed = 0;
  await env.engine.speak('3连对！ 三加五', () => completed++, assert.fail);
  assert.equal(env.sources.length, 1, 'must play PCM from pack rather than falling back to speech synthesis');
  assert.equal(env.utterances.length, 0);
  env.audioContext().currentTime = env.sources[0].buffer.duration;
  await env.sources[0].onended();
  assert.equal(completed, 1);
});

test('the first question resumes audio synchronously and waits for its voice pack', async () => {
  let finishDownload;
  const packGate = new Promise(resolve => { finishDownload = resolve; });
  const env = environment({ audioState: 'suspended', packGate });
  let completed = false;
  const speaking = env.engine.speak('三加五', () => { completed = true; }, assert.fail);
  assert.deepEqual(env.operations, ['session:playback', 'resume']);
  await Promise.resolve();
  assert.equal(env.sources.length, 0);
  assert.equal(env.utterances.length, 0, 'do not choose system speech while the pack is downloading');
  finishDownload();
  await speaking;
  assert.equal(env.sources.length, 1);
  assert.equal(completed, false);
  env.audioContext().currentTime = env.sources[0].buffer.duration;
  await env.sources[0].onended();
  assert.equal(completed, true);
});

test('exit during the first voice pack download cannot play a stale question', async () => {
  let finishDownload;
  const env = environment({ packGate: new Promise(resolve => { finishDownload = resolve; }) });
  const speaking = env.engine.speak('三加五', assert.fail, assert.fail);
  env.engine.release();
  finishDownload();
  await speaking;
  assert.equal(env.sources.length, 0);
  assert.equal(env.utterances.length, 0);
});

test('failed synthesis does not claim the question was spoken', async () => {
  const env = environment({ packAvailable: false });
  let completed = false;
  const errors = [];
  await env.engine.speak('三加五', () => { completed = true; }, error => errors.push(error));
  env.utterances[0].onerror({ error: 'not-allowed' });
  assert.equal(completed, false);
  assert.match(errors[0], /not-allowed/);
});

test('offline voice round prepares capture before first playback and retains it between questions', async () => {
  const env = environment({ search: '', prepareGate: Promise.resolve() });
  await env.ready;
  env.engine.setVoiceMode(true);
  let prepared;
  for (const text of ['三加五', '四加四']) {
    await env.engine.speak(text, () => {}, assert.fail);
    prepared = env.recognitions.at(-1);
    assert.equal(env.operations.filter(item => item === 'microphone-open').length, 1);
    assert.ok(env.operations.indexOf('capture-prepare') < env.operations.indexOf('playback-start'));
    let detected = 0, result;
    await env.engine.startListening(() => {}, () => detected++, raw => { result = raw; return true; }, assert.fail);
    assert.equal(env.engine.recognition, prepared);
    prepared.onaudiostart();
    prepared.onresult({ results: [[{ transcript: '八' }]] });
    assert.equal(result, '八');
    assert.equal(detected, 1);
    env.engine.stopAll();
    assert.equal(env.trackStopped(), false);
  }
  assert.equal(env.recognitions.length, 2, 'fresh recognizer per question');
  env.engine.release();
  assert.equal(env.trackStopped(), true);
});

test('release during offline capture preparation never starts playback and releases the microphone', async () => {
  let ready;
  const env = environment({ search: '', prepareGate: new Promise(resolve => { ready = resolve; }) });
  await env.ready;
  env.engine.setVoiceMode(true);
  const speaking = env.engine.speak('三加五', assert.fail, assert.fail);
  while (!env.engine.preparedRecognition) await Promise.resolve();
  env.engine.release();
  ready();
  await speaking;
  assert.equal(env.sources.length, 0);
  assert.equal(env.trackStopped(), true);
});

test('a microphone granted after exiting offline preparation is stopped immediately', async () => {
  let grant;
  const env = environment({
    search: '', prepareGate: Promise.resolve(),
    microphone: stream => new Promise(resolve => { grant = () => resolve(stream); })
  });
  await env.ready;
  env.engine.setVoiceMode(true);
  const speaking = env.engine.speak('三加五', assert.fail, assert.fail);
  while (!grant) await Promise.resolve();
  env.engine.release();
  grant();
  await speaking;
  assert.equal(env.sources.length, 0);
  assert.equal(env.trackStopped(), true);
});

test('switching to keypad while offline permission is pending still plays the question', async () => {
  let grant;
  const env = environment({
    search: '', prepareGate: Promise.resolve(),
    microphone: stream => new Promise(resolve => { grant = () => resolve(stream); })
  });
  await env.ready;
  env.engine.setVoiceMode(true);
  const speaking = env.engine.speak('三加五', () => {}, assert.fail);
  while (!grant) await Promise.resolve();
  env.engine.setVoiceMode(false);
  grant();
  await speaking;
  assert.equal(env.sources.length, 1);
  assert.equal(env.trackStopped(), true);
});

test('failed offline preparation reports an error instead of pretending playback completed', async () => {
  let fail;
  const env = environment({
    search: '', prepareGate: new Promise((_, reject) => { fail = reject; })
  });
  await env.ready;
  env.engine.setVoiceMode(true);
  const errors = [];
  const speaking = env.engine.speak('三加五', assert.fail, error => errors.push(error));
  while (!env.engine.preparedRecognition) await Promise.resolve();
  fail(new Error('capture failed'));
  await speaking;
  assert.equal(env.sources.length, 0);
  assert.match(errors[0], /capture failed/);
  assert.equal(env.engine.preparedRecognition, null);
  env.engine.release();
  assert.equal(env.trackStopped(), true);
});

test('asynchronous recognition startup failures are handled without an unhandled rejection', async () => {
  const env = environment();
  await env.ready;
  env.context.window.SpeechRecognition.prototype.start = async () => {
    throw new Error('startup failed');
  };
  const errors = [];
  await env.engine.startListening(assert.fail, assert.fail, assert.fail, reason => errors.push(reason));
  assert.equal(errors.length, 1);
  assert.equal(env.engine.recognition, null);
});

test('offline is the default engine even when browser speech recognition exists', async () => {
  const env = environment();
  env.context.window.location.search = '';
  assert.equal(env.engine.recognitionType(), undefined);
  const Offline = class {};
  env.context.MentalMathOfflineRecognition = Offline;
  assert.equal(env.engine.recognitionType(), Offline);
  env.context.window.location.search = '?speechEngine=browser';
  assert.equal(env.engine.recognitionType(), env.context.window.SpeechRecognition);
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

test('recognition forwards finality and empty hypotheses for shared answer confirmation', async () => {
  const env = environment();
  await env.ready;
  const observations = [];
  await env.engine.startListening(() => {}, () => {}, (text, isFinal) => {
    observations.push([text, isFinal]);
    return false;
  }, assert.fail);
  const recognition = env.recognitions[0];
  recognition.onresult({ results: [Object.assign([{ transcript: '四' }], { isFinal: false })] });
  recognition.onresult({ results: [Object.assign([{ transcript: '' }], { isFinal: false })] });
  recognition.onresult({
    resultIndex: 1,
    results: [
      Object.assign([{ transcript: '四' }], { isFinal: true }),
      Object.assign([{ transcript: '十' }], { isFinal: true })
    ]
  });
  assert.deepEqual(observations, [['四', false], ['', false], ['十', true]]);
  assert.notEqual(env.engine.recognition, null);
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
  assert.equal(env.context.navigator.audioSession.type, 'play-and-record');
  recognition.onend();
  await playback;
  assert.equal(env.sources.length, 1);
  assert.ok(env.operations.indexOf('recognition-abort') <
    env.operations.indexOf('playback-start'));
  assert.deepEqual(env.operations.filter(item => item.startsWith('session:')),
    ['session:play-and-record']);
});

test('exiting while waiting for microphone release cannot start stale playback', async () => {
  const env = environment({ deferredEnd: true });
  await env.ready;
  await env.engine.startListening(() => {}, () => {}, () => false, assert.fail);
  const playback = env.engine.speak('三加五', assert.fail, assert.fail);
  env.engine.release();
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
  while (!env.timers.some(timer => timer.delay === 3000)) await Promise.resolve();
  env.timers.find(timer => timer.delay === 3000).callback();
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
  while (!env.timers.some(timer => timer.delay === 3000)) await Promise.resolve();
  env.engine.stopAll();
  env.timers.find(timer => timer.delay === 3000).callback();
  await playback;
  assert.equal(env.sources.length, 0);
  env.recognitions[0].onend();
});

test('capture starts in a recording session only after audible playback completion', async () => {
  const env = environment({ manualTimers: true });
  await env.ready;
  env.engine.setVoiceMode(true);
  let listening;
  await env.engine.speak('三加五', () => {
    listening = env.engine.startListening(() => {}, () => {}, () => false, assert.fail);
  }, assert.fail);
  const context = env.audioContext();
  const duration = env.sources[0].buffer.duration;
  context.currentTime = duration;
  context.getOutputTimestamp = () => ({ contextTime: duration - 0.2, performanceTime: 1000 });
  env.sources[0].onended();
  assert.equal(env.context.navigator.audioSession.type, 'play-and-record');
  assert.equal(env.recognitions.length, 0);
  const drain = env.timers.find(timer => !timer.cancelled && Math.abs(timer.delay - 200) < 0.01);
  assert.ok(drain);
  await drain.callback();
  await listening;
  assert.equal(env.operations.includes('suspend-start'), false);
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
  const drain = env.timers.find(timer => !timer.cancelled && Math.abs(timer.delay - 90) < 0.01);
  assert.ok(drain);
  await drain.callback();
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
  env.engine.release();
  env.timers.find(timer => !timer.cancelled && timer.delay < 1000).callback();
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
  env.engine.setVoiceMode(false);
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

test('voice training holds one audio session across playback, capture and question settlement', async () => {
  const env = environment();
  await env.ready;
  env.engine.setVoiceMode(true);
  assert.equal(env.recognitions.length, 0, 'session selection must not open the microphone');
  for (const text of ['三加五', '四加四']) {
    let completed = false;
    await env.engine.speak(text, () => { completed = true; }, assert.fail);
    const source = env.sources.at(-1);
    env.audioContext().currentTime += source.buffer.duration;
    await source.onended();
    assert.equal(completed, true);
    await env.engine.startListening(() => {}, () => {}, () => true, assert.fail);
    env.recognitions.at(-1).onresult({ results: [[{ transcript: '八' }]] });
    env.engine.stopAll();
  }
  assert.equal(env.audioContext().state, 'running');
  assert.equal(env.operations.includes('suspend-start'), false);
  assert.deepEqual(env.operations.filter(item => item.startsWith('session:')),
    ['session:play-and-record']);
  env.engine.release();
  assert.equal(env.context.navigator.audioSession.type, 'auto');
});

test('natural recognition end and capture errors do not switch the training audio session', async () => {
  const env = environment();
  await env.ready;
  env.engine.setVoiceMode(true);
  const errors = [];
  await env.engine.startListening(() => {}, () => {}, () => false, error => errors.push(error));
  env.recognitions[0].onend();
  await env.engine.startListening(() => {}, () => {}, () => false, error => errors.push(error));
  env.recognitions[1].onerror({ error: 'network' });
  assert.equal(errors.length, 2);
  assert.deepEqual(env.operations.filter(item => item.startsWith('session:')),
    ['session:play-and-record']);
  env.engine.setVoiceMode(false);
  assert.equal(env.context.navigator.audioSession.type, 'auto');
});

test('switching to keypad cancels pending microphone startup and restores default routing', async () => {
  const env = environment({ deferredEnd: true });
  await env.ready;
  await env.engine.startListening(() => {}, () => {}, () => false, assert.fail);
  const replacement = env.engine.startListening(assert.fail, assert.fail, assert.fail, assert.fail);
  env.engine.setVoiceMode(false);
  env.recognitions[0].onend();
  await replacement;
  assert.equal(env.recognitions.length, 1);
  assert.equal(env.context.navigator.audioSession.type, 'auto');
  await env.engine.speak('三加五', () => {}, assert.fail);
  assert.equal(env.context.navigator.audioSession.type, 'playback');
});

test('release during output drain cannot restore the voice session from an old callback', async () => {
  const env = environment({ manualTimers: true });
  await env.ready;
  env.engine.setVoiceMode(true);
  await env.engine.speak('三加五', assert.fail, assert.fail);
  env.audioContext().currentTime = env.sources[0].buffer.duration;
  env.audioContext().outputLatency = 0.1;
  env.sources[0].onended();
  env.engine.release();
  env.timers.find(timer => timer.delay < 1000 && !timer.cancelled).callback();
  assert.equal(env.recognitions.length, 0);
  assert.equal(env.context.navigator.audioSession.type, 'auto');
});

test('permission priming during an active voice round does not restore automatic routing', async () => {
  const env = environment();
  await env.ready;
  env.engine.setVoiceMode(true);
  await new Promise(resolve => env.engine.requestRecordPermission(resolve));
  assert.equal(env.context.navigator.audioSession.type, 'play-and-record');
  assert.deepEqual(env.operations.filter(item => item.startsWith('session:')),
    ['session:play-and-record']);
});

test('stopping one question preserves the voice mode until explicit release', async () => {
  const env = environment();
  await env.ready;
  env.engine.setVoiceMode(true);
  env.engine.stopAll();
  assert.equal(env.context.navigator.audioSession.type, 'play-and-record');
  env.engine.release();
  assert.equal(env.context.navigator.audioSession.type, 'auto');
});

test('speech synthesis fallback also preserves the voice training session', async () => {
  const env = environment({ packAvailable: false });
  await env.ready;
  env.engine.setVoiceMode(true);
  let completed = false;
  await env.engine.speak('三加五', () => { completed = true; }, assert.fail);
  await env.utterances[0].onend();
  assert.equal(completed, true);
  assert.equal(env.audioContext().state, 'running');
  assert.equal(env.context.navigator.audioSession.type, 'play-and-record');
});


test('playFeedbackSound synthesizes gentle micro chime for correct answer and combo', async () => {
  const env = environment();
  await env.ready;
  env.engine.audioContext();
  env.engine.playFeedbackSound(false);
  assert.ok(env.operations.includes('oscillator-start'));
  env.engine.playFeedbackSound(true);
  assert.equal(env.operations.filter(op => op === 'oscillator-start').length, 3);
});