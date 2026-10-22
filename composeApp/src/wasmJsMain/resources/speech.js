class MentalMathBrowserSpeech {
  static pack = null;
  static loading = null;
  static audioContext = null;

  constructor() {
    this.token = 0;
    this.source = null;
    this.recognition = null;
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
    const context = MentalMathBrowserSpeech.audioContext;
    if (context.state === 'suspended') {
      context.resume().catch(error => console.error('Audio resume failed:', error));
    }
    return context;
  }

  hasRecordPermission() {
    return this.permissionGranted;
  }

  requestRecordPermission(onResult) {
    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!window.isSecureContext || !navigator.mediaDevices || !Recognition) {
      console.warn('Voice answers require HTTPS (or localhost) and SpeechRecognition');
      onResult(false);
      return;
    }
    try {
      this.audioContext();
      navigator.mediaDevices.getUserMedia({ audio: true }).then(stream => {
        stream.getTracks().forEach(track => track.stop());
        this.permissionGranted = true;
        onResult(true);
      }).catch(error => {
        console.error('Microphone permission failed:', error);
        onResult(false);
      });
    } catch (error) {
      console.error('Audio initialization failed:', error);
      onResult(false);
    }
  }

  speak(text, onDone) {
    this.stopAll();
    const token = this.token;
    const done = () => {
      if (this.token === token) onDone();
    };
    const pack = MentalMathBrowserSpeech.pack;
    const slice = pack && pack.phrases.get(text);
    if (slice) {
      try {
        const context = this.audioContext();
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
        source.onended = () => {
          if (this.token === token) {
            this.source = null;
            done();
          }
        };
        source.start();
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
    } else {
      console.error('No audio playback or speech synthesis is available');
      setTimeout(done, 500);
    }
  }

  startListening(onSpeechDetected, onCandidates, onError) {
    this.stopListening();
    const token = this.token;
    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!Recognition || !window.isSecureContext) {
      onError('当前浏览器或连接不支持语音识别，请使用按键模式');
      return;
    }
    try {
      const recognition = new Recognition();
      this.recognition = recognition;
      recognition.lang = 'zh-CN';
      recognition.continuous = false;
      recognition.interimResults = true;
      recognition.maxAlternatives = 5;
      let detected = false;
      let delivered = false;
      const active = () => this.token === token && this.recognition === recognition && !delivered;
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
          onError('未听清答案，可使用按键作答');
        }
      };
      recognition.onend = () => {
        if (active()) {
          this.recognition = null;
          onError('语音识别已结束，可使用按键作答');
        }
      };
      recognition.start();
    } catch (error) {
      console.error('Speech recognition could not start:', error);
      onError('语音启动失败，请使用按键模式');
    }
  }

  stopListening() {
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
}

globalThis.MentalMathBrowserSpeech = MentalMathBrowserSpeech;
