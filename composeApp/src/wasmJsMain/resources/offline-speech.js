(() => {
  const grammar = JSON.stringify(['零', '一', '二', '三', '四', '五', '六', '七', '八', '九', '十', '两', '[unk]']);
  const modelUrl = new URL('vosk-model-cn.tar.gz', document.baseURI).href;
  let modelPromise = null;
  const workletPromises = new WeakMap();
  const listeners = new Set();
  const preparationListeners = new Set();
  let preparation = { status: 'idle' };
  const diagnostic = (event, detail = '') => window.mentalMathDiagnostics?.record(event, detail);

  function setPreparation(status, message = '') {
    preparation = { status, message };
    for (const listener of preparationListeners) listener(preparation);
  }

  function loadModel() {
    if (modelPromise) return modelPromise;
    setPreparation('loading');
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
        setPreparation('error', error.message);
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
          setPreparation('ready');
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
      if (preparation.status !== 'error') setPreparation('error', error.message);
      throw error;
    });
    return modelPromise;
  }

  function loadCaptureModule(context) {
    let loading = workletPromises.get(context);
    if (!loading) {
      loading = context.audioWorklet.addModule(
        new URL('offline-capture.js', document.baseURI).href
      ).catch(error => {
        workletPromises.delete(context);
        throw error;
      });
      workletPromises.set(context, loading);
    }
    return loading;
  }

  class MentalMathOfflineRecognition extends EventTarget {
    static observePreparation(listener) {
      preparationListeners.add(listener);
      listener(preparation);
      return () => preparationListeners.delete(listener);
    }

    static preload() {
      return loadModel();
    }

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
      this.preparing = null;
      this.ownsStream = true;
      this.capturing = false;
      this.captureReady = new Promise(resolve => { this.resolveCaptureReady = resolve; });
    }

    emit(type, detail = {}) {
      const event = Object.assign(new Event(type), detail);
      this.dispatchEvent(event);
      this['on' + type]?.(event);
    }

    async start() {
      if (this.startRequested || this.stopped) throw new Error('Recognition already started or stopped');
      this.startRequested = true;
      try {
        if (this.preparing) await this.preparing;
        else await this.initialize();
        if (this.stopped) return;
        this.started = true;
        this.listeningFromTime = globalThis.MentalMathBrowserSpeech.audioContext.currentTime;
        if (this.capturing) {
          this.ready = true;
          this.emit('audiostart');
        }
      } catch (error) {
        this.fail(error);
      }
    }

    async prepare(stream) {
      this.preparing = this.initialize(stream);
      await this.preparing;
      if (this.stopped) throw this.failure ||
        Object.assign(new Error('离线收音准备已取消'), { name: 'AbortError' });
      const timeout = setTimeout(() => this.fail(new Error('麦克风收音准备超时')), 5000);
      try {
        if (!await this.captureReady) throw this.failure ||
          Object.assign(new Error('离线收音准备已取消'), { name: 'AbortError' });
      } finally {
        clearTimeout(timeout);
      }
    }

    async initialize(preparedStream) {
      try {
        const model = await loadModel();
        if (this.stopped) return;
        listeners.add(this);
        const context = globalThis.MentalMathBrowserSpeech.audioContext;
        if (!context?.audioWorklet) throw new Error('当前浏览器不支持离线收音，请更新 Safari');
        await loadCaptureModule(context);
        if (this.stopped) return;
        this.ownsStream = !preparedStream;
        const stream = preparedStream || await navigator.mediaDevices.getUserMedia({
          audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true }
        });
        if (this.stopped) {
          if (this.ownsStream) stream.getTracks().forEach(track => track.stop());
          return;
        }
        this.stream = stream;
        this.recognizer = new model.KaldiRecognizer(context.sampleRate, grammar);
        this.recognizer.on('error', () => this.fail(new Error('离线数字识别失败')));
        for (const type of ['result', 'partialresult']) {
          this.recognizer.on(type, message => {
            this.pending = Math.max(0, this.pending - 1);
            if (this.stopped || !this.started) return;
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
            this.capturing = true;
            this.resolveCaptureReady(true);
            if (!this.started) return;
            const packet = event.data;
            let samples = packet.samples;
            const skipped = Math.max(0, Math.ceil(
              (this.listeningFromTime - packet.startTime) * context.sampleRate
            ));
            if (skipped >= samples.length) return;
            if (skipped > 0) samples = samples.slice(skipped);
            if (!this.ready) {
              this.ready = true;
              this.emit('audiostart');
              if (this.stopped) return;
            }
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
      this.failure = error;
      console.error('Offline recognition failed:', error);
      diagnostic('offline_capture_error');
      this.emit('error', { error: 'offline-unavailable', message: error.message });
      this.abort();
    }

    abort() {
      if (this.stopped) return;
      this.stopped = true;
      this.resolveCaptureReady(false);
      listeners.delete(this);
      if (this.node) {
        this.node.port.onmessage = null;
        this.node.onprocessorerror = null;
        this.node.disconnect();
        this.node.port.close();
      }
      this.source?.disconnect();
      if (this.stream) {
        this.stream.getAudioTracks()[0].onended = null;
        if (this.ownsStream) this.stream.getTracks().forEach(track => track.stop());
      }
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
