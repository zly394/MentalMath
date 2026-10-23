class MentalMathCapture extends AudioWorkletProcessor {
  constructor() {
    super();
    this.samples = new Float32Array(1024);
    this.offset = 0;
  }

  process(inputs) {
    const channel = inputs[0]?.[0];
    if (!channel) return true;
    for (let index = 0; index < channel.length; index++) {
      const sample = channel[index];
      if (this.offset === 0) this.startTime = (currentFrame + index) / sampleRate;
      this.samples[this.offset++] = sample;
      if (this.offset === this.samples.length) {
        this.port.postMessage({ samples: this.samples, startTime: this.startTime }, [this.samples.buffer]);
        this.samples = new Float32Array(1024);
        this.offset = 0;
      }
    }
    return true;
  }
}

registerProcessor('mentalmath-capture', MentalMathCapture);
