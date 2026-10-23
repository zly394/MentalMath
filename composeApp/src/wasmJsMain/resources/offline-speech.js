(() => {
  const grammar = JSON.stringify(['零', '一', '二', '三', '四', '五', '六', '七', '八', '九', '十', '两', '[unk]']);
  const modelUrl = new URL('vosk-model-cn.tar.gz', document.baseURI).href;
  let modelPromise = null;
  const listeners = new Set();
  const diagnostic = (event, detail = '') => window.mentalMathDiagnostics?.record(event, detail);

  function loadModel() {
    if (modelPromise) return modelPromise;
    modelPromise = new Promise((resolve, reject) => {
      if (!globalThis.mentalMathVosk) {
        reject(new Error('离线识别组件未加载'));
        return;
      }
      diagnostic('offline_model_loading');
      const model = new globalThis.mentalMathVosk.Model(modelUrl, -1);
      let settled = false;
      let failed = false;
      const fail = error => {
        if (failed) return;
        failed = true;
        clearTimeout(timeout);
        diagnostic('offline_model_error');
        modelPromise = null;
        model.terminate();
        if (!settled) {
          settled = true;
          reject(error);
        } else {
          for (const listener of [...listeners]) listener.fail(error);
        }
      };
      const timeout = setTimeout(() =>
        fail(new Error('离线模型准备超时，请检查连接后重试')), 90000);
      model.on('load', message => {
        if (settled) return;
        clearTimeout(timeout);
        if (message.result) {
          settled = true;
          diagnostic('offline_model_ready');
          resolve(model);
        } else {
          fail(new Error('离线模型加载失败'));
        }
      });
      model.on('error', message => {
        fail(new Error(message.error || '离线模型加载失败'));
      });
    }).catch(error => {
      modelPromise = null;
      throw error;
    });
    return modelPromise;
  }

  class MentalMathOfflineRecognition extends EventTarget {
    constructor() {
      super();
      this.stopped = false;
      this.stream = null;
      this.source = null;
      this.node = null;
      this.recognizer = null;
      this.detected = false;
      this.ready = false;
      this.pending = 0;
      this.started = false;
    }

    emit(type, detail = {}) {
      const event = Object.assign(new Event(type), detail);
      this.dispatchEvent(event);
      this['on' + type]?.(event);
    }

    async start() {
      if (this.started || this.stopped) throw new Error('Recognition already started or stopped');
      this.started = true;
      try {
        const model = await loadModel();
        if (this.stopped) return;
        listeners.add(this);
        const context = globalThis.MentalMathBrowserSpeech.audioContext;
        if (!context?.audioWorklet) throw new Error('当前浏览器不支持离线收音，请更新 Safari');
        await context.audioWorklet.addModule(new URL('offline-capture.js', document.baseURI).href);
        if (this.stopped) return;
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true }
        });
        if (this.stopped) {
          stream.getTracks().forEach(track => track.stop());
          return;
        }
        this.stream = stream;
        this.recognizer = new model.KaldiRecognizer(context.sampleRate, grammar);
        this.recognizer.on('error', () => this.fail(new Error('离线数字识别失败')));
        for (const type of ['result', 'partialresult']) {
          this.recognizer.on(type, message => {
            this.pending = Math.max(0, this.pending - 1);
            if (this.stopped) return;
            const text = (message.result.text || message.result.partial || '').trim();
            if (!text || text === '[unk]') return;
            this.detect();
            this.emit('result', {
              resultIndex: 0,
              results: [Object.assign([{ transcript: text.replace(/\s/g, '') }], {
                isFinal: type === 'result'
              })]
            });
          });
        }
        this.source = context.createMediaStreamSource(stream);
        this.node = new AudioWorkletNode(context, 'mentalmath-capture');
        this.node.onprocessorerror = () => this.fail(new Error('离线收音处理失败'));
        stream.getAudioTracks()[0].onended = () => {
          if (!this.stopped) this.fail(new Error('麦克风已断开'));
        };
        this.node.port.onmessage = event => {
          try {
            if (this.stopped) return;
            if (!this.ready) {
              this.ready = true;
              this.emit('audiostart');
              if (this.stopped) return;
            }
            const samples = event.data;
            let energy = 0;
            for (const sample of samples) energy += sample * sample;
            if (Math.sqrt(energy / samples.length) > 380 / 32768) this.detect();
            if (this.stopped) return;
            // Fail explicitly rather than accumulate seconds of recognition lag.
            if (this.pending >= Math.ceil(context.sampleRate * 2 / samples.length)) {
              this.fail(new Error('设备离线识别处理过慢，请使用按键模式'));
              return;
            }
            this.pending++;
            this.recognizer.acceptWaveformFloat(samples, context.sampleRate);
          } catch (error) {
            this.fail(error);
          }
        };
        this.source.connect(this.node);
        this.node.connect(context.destination);
        this.emit('start');
        diagnostic('offline_capture_started');
      } catch (error) {
        this.fail(error);
      }
    }

    detect() {
      if (!this.detected && this.ready && !this.stopped) {
        this.detected = true;
        this.emit('speechstart');
      }
    }

    fail(error) {
      if (this.stopped) return;
      console.error('Offline recognition failed:', error);
      diagnostic('offline_capture_error');
      this.emit('error', { error: 'offline-unavailable', message: error.message });
      this.abort();
    }

    abort() {
      if (this.stopped) return;
      this.stopped = true;
      listeners.delete(this);
      if (this.node) {
        this.node.port.onmessage = null;
        this.node.onprocessorerror = null;
        this.node.disconnect();
        this.node.port.close();
      }
      this.source?.disconnect();
      this.stream?.getTracks().forEach(track => track.stop());
      try {
        this.recognizer?.remove();
      } catch (error) {
        console.error('Offline recognizer cleanup failed:', error);
        diagnostic('offline_capture_error');
      }
      this.recognizer = null;
      this.stream = null;
      this.source = null;
      this.node = null;
      this.emit('audioend');
      this.emit('end');
    }
  }

  globalThis.MentalMathOfflineRecognition = MentalMathOfflineRecognition;
})();
