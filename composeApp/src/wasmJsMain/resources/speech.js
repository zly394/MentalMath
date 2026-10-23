class MentalMathBrowserSpeech {
  static pack = null;
  static loading = null;
  static audioContext = null;

  constructor() {
    this.token = 0;
    this.source = null;
    this.recognition = null;
    this.recognitionEnd = null;
    this.listeningToken = 0;
    this.voiceMode = false;
    this.permissionGranted = false;
    if (!MentalMathBrowserSpeech.loading) {
      MentalMathBrowserSpeech.loading = this.loadPack().catch(error => {
        console.error('Failed to load phrases.pak; using speech synthesis:', error);
      });
    }
  }

  async loadPack() {
    const response = await fetch('phrases.pak');
    if (!response.ok) throw new Error(`phrases.pak: HTTP ${response.status}`);
    const buffer = await response.arrayBuffer();
    const view = new DataView(buffer);
    if (view.getUint32(0) !== 0x4d50414b) throw new Error('Invalid voice pack header');
    const count = view.getInt32(4);
    const phrases = new Map();
    const decoder = new TextDecoder();
    let pos = 8;
    for (let i = 0; i < count; i++) {
      const keyLength = view.getUint16(pos);
      pos += 2;
      const key = decoder.decode(new Uint8Array(buffer, pos, keyLength));
      pos += keyLength;
      phrases.set(key, { offset: view.getInt32(pos), length: view.getInt32(pos + 4) });
      pos += 8;
    }
    MentalMathBrowserSpeech.pack = { buffer, phrases, dataOffset: pos };
  }

  audioContext() {
    if (!MentalMathBrowserSpeech.audioContext) {
      const AudioContext = window.AudioContext || window.webkitAudioContext;
      MentalMathBrowserSpeech.audioContext = new AudioContext();
    }
    return MentalMathBrowserSpeech.audioContext;
  }

  setAudioSession(type) {
    if (!navigator.audioSession) return;
    try {
      if (navigator.audioSession.type === type) return;
      navigator.audioSession.type = type;
      window.mentalMathDiagnostics?.record('audio_session', type);
    } catch (error) {
      console.warn('Audio session selection is unavailable:', error);
    }
  }

  setVoiceMode(enabled) {
    this.voiceMode = enabled;
    if (!enabled) this.stopListening();
    this.setAudioSession(enabled ? 'play-and-record' : 'auto');
  }

  async waitForRecognitionEnd() {
    const pending = this.recognitionEnd;
    if (!pending) return;
    window.mentalMathDiagnostics?.record('recognition_release_wait');
    await new Promise((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error('Microphone release timed out')), 3000);
      pending.then(() => {
        clearTimeout(timeout);
        resolve();
      });
    });
  }

  finishPlayback(context, endTime, done) {
    // Buffer completion precedes the last audible samples at the output device.
    const timestamp = context.getOutputTimestamp?.();
    const delayMs = timestamp && timestamp.contextTime > 0
      ? timestamp.performanceTime + (endTime - timestamp.contextTime) * 1000 - performance.now()
      : ((context.baseLatency || 0) + (context.outputLatency || 0) +
          endTime - context.currentTime) * 1000;
    if (delayMs > 0) {
      setTimeout(done, delayMs);
    } else {
      return done();
    }
  }

  hasRecordPermission() {
    return this.permissionGranted;
  }

  recognitionType() {
    return new URLSearchParams(window.location.search).get('speechEngine') === 'browser'
      ? window.SpeechRecognition || window.webkitSpeechRecognition
      : globalThis.MentalMathOfflineRecognition;
  }

  requestRecordPermission(onResult) {
    const Recognition = this.recognitionType();
    if (!window.isSecureContext || !navigator.mediaDevices || !Recognition) {
      console.warn('Voice answers require HTTPS (or localhost) and a recognition engine');
      onResult(false);
      return;
    }
    try {
      this.setAudioSession('play-and-record');
      const context = this.audioContext();
      if (context.state !== 'running') {
        context.resume().catch(error => console.error('Audio resume failed:', error));
      }
      navigator.mediaDevices.getUserMedia({ audio: true }).then(stream => {
        stream.getTracks().forEach(track => track.stop());
        this.setAudioSession(this.voiceMode ? 'play-and-record' : 'auto');
        this.permissionGranted = true;
        onResult(true);
      }).catch(error => {
        console.error('Microphone permission failed:', error);
        this.setAudioSession(this.voiceMode ? 'play-and-record' : 'auto');
        onResult(false);
      });
    } catch (error) {
      console.error('Audio initialization failed:', error);
      this.setAudioSession(this.voiceMode ? 'play-and-record' : 'auto');
      onResult(false);
    }
  }

  async speak(text, onDone, onError) {
    this.stopAll();
    const token = this.token;
    const done = () => {
      if (this.token === token) {
        window.mentalMathDiagnostics?.record('playback_done');
        onDone();
      }
    };
    let context;
    try {
      await this.waitForRecognitionEnd();
      if (this.token !== token) return;
      this.setAudioSession(this.voiceMode ? 'play-and-record' : 'playback');
      context = this.audioContext();
      if (context.state !== 'running') await context.resume();
      if (this.token !== token) return;
    } catch (error) {
      if (this.token !== token) return;
      console.error('Audio playback preparation failed:', error);
      window.mentalMathDiagnostics?.record('playback_failure');
      onError('播题音频准备失败');
      return;
    }
    const pack = MentalMathBrowserSpeech.pack;
    const slice = pack && pack.phrases.get(text);
    if (slice) {
      try {
        const audio = context.createBuffer(1, slice.length / 2, 16000);
        const samples = audio.getChannelData(0);
        const view = new DataView(pack.buffer, pack.dataOffset + slice.offset, slice.length);
        for (let i = 0; i < samples.length; i++) {
          samples[i] = view.getInt16(i * 2, true) / 32768;
        }
        const source = context.createBufferSource();
        source.buffer = audio;
        source.connect(context.destination);
        this.source = source;
        const startTime = context.currentTime;
        const endTime = startTime + audio.duration;
        source.onended = () => {
          if (this.token === token) {
            this.source = null;
            source.disconnect();
            return this.finishPlayback(context, endTime, done);
          }
        };
        source.start(startTime);
        window.mentalMathDiagnostics?.record('playback_start', 'pcm');
        return;
      } catch (error) {
        console.error('Voice pack playback failed; using speech synthesis:', error);
      }
    }
    if (window.speechSynthesis) {
      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = 'zh-CN';
      utterance.rate = 1.15;
      utterance.onend = done;
      utterance.onerror = event => {
        console.error('Speech synthesis failed:', event.error);
        done();
      };
      window.speechSynthesis.speak(utterance);
      window.mentalMathDiagnostics?.record('playback_start', 'synthesis');
    } else {
      console.error('No audio playback or speech synthesis is available');
      setTimeout(done, 500);
    }
  }

  async startListening(onListeningReady, onSpeechDetected, onCandidates, onError) {
    this.stopListening();
    const token = this.token;
    const listeningToken = this.listeningToken;
    const current = () => this.token === token && this.listeningToken === listeningToken;
    const Recognition = this.recognitionType();
    if (!Recognition || !window.isSecureContext) {
      onError('当前浏览器或连接不支持语音识别，请使用按键模式');
      return;
    }
    let finishRecognition;
    try {
      await this.waitForRecognitionEnd();
      if (!current()) return;
      this.setVoiceMode(true);
      const recognition = new Recognition();
      window.mentalMathDiagnostics?.observeRecognition(recognition);
      this.recognition = recognition;
      recognition.lang = 'zh-CN';
      recognition.continuous = false;
      recognition.interimResults = true;
      recognition.maxAlternatives = 5;
      const ended = new Promise(resolve => { finishRecognition = resolve; });
      this.recognitionEnd = ended;
      let detected = false;
      let delivered = false;
      let ready = false;
      const active = () => current() && this.recognition === recognition && !delivered;
      recognition.onaudiostart = () => {
        if (active() && !ready) {
          ready = true;
          onListeningReady();
        }
      };
      const detect = () => {
        if (active() && !detected) {
          detected = true;
          onSpeechDetected();
        }
      };
      recognition.onspeechstart = detect;
      recognition.onresult = event => {
        if (!active()) return;
        const candidates = [];
        for (let i = 0; i < event.results.length; i++) {
          const result = event.results[i];
          for (let j = 0; j < result.length; j++) {
            const text = result[j].transcript.trim();
            if (text) candidates.push(text);
          }
        }
        if (candidates.length) {
          detect();
          if (onCandidates(candidates.join('\n'))) {
            delivered = true;
            if (this.recognition === recognition) this.stopListening();
          }
        }
      };
      recognition.onerror = event => {
        if (active()) {
          console.error('Speech recognition failed:', event.error);
          const reasons = {
            'not-allowed': '未授予麦克风或语音识别权限',
            'service-not-allowed': '未授予麦克风或语音识别权限',
            'audio-capture': '无法使用麦克风',
            'network': '语音识别服务连接失败'
          };
          this.stopListening();
          onError(event.error === 'offline-unavailable'
            ? event.message || '离线识别不可用，请使用按键模式'
            : reasons[event.error] || '未听清答案，可使用按键作答');
        }
      };
      recognition.onend = () => {
        finishRecognition();
        if (this.recognitionEnd === ended) this.recognitionEnd = null;
        if (active()) {
          this.recognition = null;
          onError('语音识别已结束，可使用按键作答');
        }
      };
      recognition.start();
    } catch (error) {
      if (!current()) return;
      if (finishRecognition) {
        finishRecognition();
        this.recognitionEnd = null;
      }
      console.error('Speech recognition could not start:', error);
      this.stopListening();
      onError('语音启动失败，请使用按键模式');
    }
  }

  stopListening() {
    this.listeningToken++;
    const recognition = this.recognition;
    this.recognition = null;
    if (recognition) recognition.abort();
  }

  stopAll() {
    this.token++;
    const source = this.source;
    this.source = null;
    if (source) {
      source.onended = null;
      source.stop();
      source.disconnect();
    }
    if (window.speechSynthesis) window.speechSynthesis.cancel();
    this.stopListening();
  }

  release() {
    this.stopAll();
    this.setVoiceMode(false);
  }
}

globalThis.MentalMathBrowserSpeech = MentalMathBrowserSpeech;
