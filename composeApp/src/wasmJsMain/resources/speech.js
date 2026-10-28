class MentalMathBrowserSpeech {
  static pack = null;
  static loading = null;
  static audioContext = null;
  static lifecycleCleanup = null;

  constructor() {
    this.token = 0;
    this.source = null;
    this.recognition = null;
    this.recognitionEnd = null;
    this.listeningToken = 0;
    this.voiceMode = false;
    this.permissionGranted = false;
    this.captureStream = null;
    this.capturePromise = null;
    this.captureGeneration = 0;
    this.preparedRecognition = null;
    this.gestureResume = null;
    this.pauseListener = null;
    this.playbackTimer = null;
    const pause = () => {
      window.mentalMathDiagnostics?.record('audio_background_pause');
      this.release();
      this.discardAudioContext();
      this.pauseListener?.();
    };
    const visibility = () => {
      if (document.visibilityState === 'hidden') pause();
    };
    MentalMathBrowserSpeech.lifecycleCleanup?.();
    globalThis.document?.addEventListener('visibilitychange', visibility);
    window.addEventListener?.('pagehide', pause);
    MentalMathBrowserSpeech.lifecycleCleanup = () => {
      globalThis.document?.removeEventListener('visibilitychange', visibility);
      window.removeEventListener?.('pagehide', pause);
    };
    if (!MentalMathBrowserSpeech.loading) {
      MentalMathBrowserSpeech.loading = this.loadPack().catch(error => {
        console.error('Failed to load phrases.pak; using speech synthesis:', error);
      });
    }
    if (window.isSecureContext && navigator.mediaDevices) {
      this.recognitionType()?.preload?.().catch(error => {
        console.error('Offline model preload failed; voice answers will retry:', error);
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
    if (!MentalMathBrowserSpeech.audioContext ||
        MentalMathBrowserSpeech.audioContext.state === 'closed') {
      const AudioContext = window.AudioContext || window.webkitAudioContext;
      MentalMathBrowserSpeech.audioContext = new AudioContext();
    }
    return MentalMathBrowserSpeech.audioContext;
  }

  setPauseListener(listener) {
    this.pauseListener = listener;
  }

  discardAudioContext() {
    const context = this.audioContext();
    MentalMathBrowserSpeech.audioContext = null;
    this.gestureResume = null;
    this.recognitionEnd = null;
    if (context && context.state !== 'closed') {
      context.close?.().catch(error => console.warn('Audio context cleanup failed:', error));
    }
  }

  async bounded(promise, milliseconds, message) {
    let timer;
    try {
      return await Promise.race([
        promise,
        new Promise((_, reject) => {
          timer = setTimeout(() => reject(new Error(message)), milliseconds);
        })
      ]);
    } finally {
      clearTimeout(timer);
    }
  }

  prepareFromGesture() {
    try {
      this.setAudioSession(this.voiceMode ? 'play-and-record' : 'playback');
      const context = this.audioContext();
      const promise = this.bounded(
        context.state === 'running' ? Promise.resolve() : context.resume(),
        5000, '音频恢复超时，请点击继续重试'
      ).then(() => {
        if (context.state !== 'running') throw new Error('音频尚未恢复，请点击继续');
      });
      this.gestureResume = { context, promise };
      // The actual playback consumes this rejection after the screen is visible.
      promise.catch(error => console.error('Audio gesture recovery failed:', error));
    } catch (error) {
      console.error('Audio gesture recovery failed:', error);
      this.discardAudioContext();
    }
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
    if (!enabled) {
      this.stopListening();
      this.releaseCapture();
    }
    this.setAudioSession(enabled ? 'play-and-record' : 'auto');
  }

  releaseCapture() {
    this.captureGeneration++;
    this.capturePromise = null;
    this.captureStream?.getTracks().forEach(track => track.stop());
    this.captureStream = null;
  }

  acquireCapture() {
    if (this.captureStream?.getAudioTracks().every(track => track.readyState === 'live')) {
      return Promise.resolve(this.captureStream);
    }
    if (!this.capturePromise) {
      const generation = this.captureGeneration;
      const pending = navigator.mediaDevices.getUserMedia({
        audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true }
      }).then(stream => {
        if (generation !== this.captureGeneration || !this.voiceMode) {
          stream.getTracks().forEach(track => track.stop());
          throw Object.assign(new Error('语音准备已取消'), { name: 'AbortError' });
        }
        this.captureStream = stream;
        return stream;
      });
      this.capturePromise = this.bounded(pending, 15000, '麦克风准备超时').catch(error => {
        if (generation === this.captureGeneration) this.releaseCapture();
        throw error;
      }).finally(() => {
        if (generation === this.captureGeneration) this.capturePromise = null;
      });
    }
    return this.capturePromise;
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
        clearTimeout(this.playbackTimer);
        window.mentalMathDiagnostics?.record('playback_done');
        onDone();
      }
    };
    let context;
    try {
      this.setAudioSession(this.voiceMode ? 'play-and-record' : 'playback');
      context = this.audioContext();
      // Resume within the initiating gesture, before any asynchronous preparation.
      const gesture = this.gestureResume;
      this.gestureResume = null;
      await (gesture?.context === context ? gesture.promise : this.bounded(
        context.state !== 'running' ? context.resume() : Promise.resolve(),
        5000, '音频恢复超时，请点击继续重试'
      ));
      if (context.state !== 'running') throw new Error('音频未恢复');
      if (this.token !== token) return;
      await this.waitForRecognitionEnd();
      await this.bounded(MentalMathBrowserSpeech.loading, 90000, '播报资源加载超时');
      if (this.token !== token) return;
      const Recognition = this.recognitionType();
      if (this.voiceMode && Recognition?.prototype.prepare) {
        const stream = await this.acquireCapture();
        if (this.token !== token) return;
        if (context.state !== 'running') {
          await this.bounded(context.resume(), 5000, '音频恢复超时');
        }
        if (this.voiceMode && this.token === token) {
          const recognition = new Recognition();
          this.preparedRecognition = recognition;
          recognition.onerror = event => {
            if (this.token !== token || this.preparedRecognition !== recognition) return;
            this.stopAll();
            onError(event.message || '离线收音准备失败');
          };
          await this.bounded(recognition.prepare(stream), 100000, '离线收音准备超时');
        }
        if (this.token !== token) return;
      }
      if (context.state !== 'running') {
        await this.bounded(context.resume(), 5000, '音频恢复超时');
      }
      if (context.state !== 'running') throw new Error('音频未恢复');
      if (this.token !== token) return;
    } catch (error) {
      if (this.token !== token) return;
      if (error.name !== 'AbortError' || this.voiceMode) {
        this.stopListening();
        this.releaseCapture();
        this.discardAudioContext();
        console.error('Audio playback preparation failed:', error);
        window.mentalMathDiagnostics?.record('playback_failure');
        onError('播题或麦克风准备失败：' + error.message);
        return;
      }
    }
    const pack = MentalMathBrowserSpeech.pack;
    let slice = pack && pack.phrases.get(text);
    if (!slice && pack) {
      const match = text.match(/[零一二三四五六七八九十]+[加减][零一二三四五六七八九十]+/);
      if (match) slice = pack.phrases.get(match[0]);
    }
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
        this.playbackTimer = setTimeout(() => {
          if (this.token !== token) return;
          this.release();
          this.discardAudioContext();
          onError('播报未能完成，请点击继续重试');
        }, audio.duration * 1000 + 5000);
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
      utterance.onend = () => {
        clearTimeout(this.playbackTimer);
        done();
      };
      utterance.onerror = event => {
        if (this.token !== token) return;
        clearTimeout(this.playbackTimer);
        console.error('Speech synthesis failed:', event.error);
        onError('题目播报失败：' + event.error);
      };
      window.speechSynthesis.speak(utterance);
      this.playbackTimer = setTimeout(() => {
        if (this.token !== token) return;
        this.release();
        this.discardAudioContext();
        onError('系统播报超时，请点击继续重试');
      }, 15000);
      window.mentalMathDiagnostics?.record('playback_start', 'synthesis');
    } else {
      console.error('No audio playback or speech synthesis is available');
      onError('当前浏览器无法播报题目');
    }
  }

  playFeedbackSound(isCombo) {
    try {
      const context = MentalMathBrowserSpeech.audioContext;
      if (!context || context.state !== 'running' || !context.createOscillator) return;
      const now = context.currentTime;
      if (!isCombo) {
        // 轻快水滴微音（~120ms）：880Hz -> 1320Hz 柔和快速滑音，极轻音量
        const osc = context.createOscillator();
        const gain = context.createGain();
        osc.type = 'sine';
        osc.frequency.setValueAtTime(880, now);
        osc.frequency.exponentialRampToValueAtTime(1320, now + 0.08);
        gain.gain.setValueAtTime(0.16, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.12);
        osc.connect(gain);
        gain.connect(context.destination);
        osc.start(now);
        osc.stop(now + 0.12);
      } else {
        // 连对欢呼双音（~200ms）：C6 (1046Hz) + G6 (1568Hz) 灵动跳音
        const osc1 = context.createOscillator();
        const gain1 = context.createGain();
        osc1.type = 'sine';
        osc1.frequency.setValueAtTime(1046, now);
        gain1.gain.setValueAtTime(0.18, now);
        gain1.gain.exponentialRampToValueAtTime(0.001, now + 0.08);
        osc1.connect(gain1);
        gain1.connect(context.destination);
        osc1.start(now);
        osc1.stop(now + 0.08);

        const osc2 = context.createOscillator();
        const gain2 = context.createGain();
        osc2.type = 'sine';
        osc2.frequency.setValueAtTime(1318, now + 0.07);
        osc2.frequency.exponentialRampToValueAtTime(1568, now + 0.18);
        gain2.gain.setValueAtTime(0.001, now);
        gain2.gain.setValueAtTime(0.2, now + 0.07);
        gain2.gain.exponentialRampToValueAtTime(0.001, now + 0.22);
        osc2.connect(gain2);
        gain2.connect(context.destination);
        osc2.start(now + 0.07);
        osc2.stop(now + 0.22);
      }
    } catch (e) {
      console.warn('Feedback sound playback failed:', e);
    }
  }

  async startListening(onListeningReady, onSpeechDetected, onCandidates, onError) {
    const prepared = this.preparedRecognition;
    this.preparedRecognition = null;
    this.stopListening();
    const token = this.token;
    const listeningToken = this.listeningToken;
    const current = () => this.token === token && this.listeningToken === listeningToken;
    const Recognition = this.recognitionType();
    if (!Recognition || !window.isSecureContext) {
      prepared?.abort();
      onError('当前浏览器或连接不支持语音识别，请使用按键模式');
      return;
    }
    let finishRecognition;
    try {
      await this.waitForRecognitionEnd();
      if (!current()) {
        prepared?.abort();
        return;
      }
      this.setVoiceMode(true);
      const recognition = prepared || new Recognition();
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
        let isFinal = false;
        const resultIndex = event.resultIndex ?? 0;
        for (let i = resultIndex; i < event.results.length; i++) {
          const result = event.results[i];
          if (i === resultIndex) isFinal = result.isFinal === true;
          for (let j = 0; j < result.length; j++) {
            const text = result[j].transcript.trim();
            candidates.push(text);
          }
        }
        if (candidates.some(text => text)) detect();
        if (active()) {
          if (onCandidates(candidates.join('\n'), isFinal)) {
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
      await recognition.start();
    } catch (error) {
      prepared?.abort();
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
    this.preparedRecognition?.abort();
    this.preparedRecognition = null;
    const recognition = this.recognition;
    this.recognition = null;
    if (recognition) recognition.abort();
  }

  stopAll() {
    clearTimeout(this.playbackTimer);
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
