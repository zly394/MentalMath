const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'composeApp', 'src',
  'wasmJsMain', 'resources', 'offline-status.js'), 'utf8');

function environment({ retryError = false } = {}) {
  const elements = {};
  const timers = [];
  const errors = [];
  let notify, retries = 0;
  vm.runInNewContext(source, {
    document: {
      getElementById(id) {
        return elements[id] ||= {
          hidden: true, textContent: '', value: 0,
          removeAttribute(name) { delete this[name]; }
        };
      }
    },
    MentalMathOfflineRecognition: {
      observePreparation(listener) { notify = listener; listener({ status: 'idle' }); },
      async preload() {
        retries++;
        notify({ status: 'loading' });
        if (retryError) {
          notify({ status: 'error', message: 'retry unavailable' });
          throw new Error('retry unavailable');
        }
      }
    },
    console: { error: (...args) => errors.push(args) },
    setTimeout(callback, delay) {
      const timer = { callback, delay };
      timers.push(timer);
      return timer;
    },
    clearTimeout(timer) { if (timer) timer.cancelled = true; }
  });
  return {
    elements, timers, errors, retries: () => retries,
    notify: (status, message) => notify({ status, message }),
    panel: elements['offline-status'], title: elements['offline-status-title'],
    detail: elements['offline-status-detail'], progress: elements['offline-status-progress'],
    retry: elements['offline-status-retry'], close: elements['offline-status-close']
  };
}

test('loading shows an indeterminate progress indicator without a fabricated percentage', () => {
  const env = environment();
  assert.equal(env.panel.hidden, true);
  env.notify('loading');
  assert.equal(env.panel.hidden, false);
  assert.equal(env.progress.hidden, false);
  assert.equal('value' in env.progress, false);
  assert.match(env.title.textContent, /正在准备/);
  assert.match(env.detail.textContent, /42 MiB/);
  assert.equal(env.retry.hidden, true);
});

test('ready shows completion then hides; another preparation cancels the old hide timer', () => {
  const env = environment();
  env.notify('loading');
  env.notify('ready');
  assert.match(env.title.textContent, /已就绪/);
  assert.equal(env.progress.hidden, true);
  assert.equal(env.panel.hidden, false);
  assert.equal(env.timers[0].delay, 3000);
  env.timers[0].callback();
  assert.equal(env.panel.hidden, true);
  env.notify('loading');
  assert.equal(env.panel.hidden, false);
  assert.equal(env.timers[0].cancelled, true);
});

test('errors are text, stop progress and expose retry without blocking keypad answers', async () => {
  const env = environment();
  env.notify('error', '<script>network failed</script>');
  assert.equal(env.progress.hidden, true);
  assert.equal(env.retry.hidden, false);
  assert.match(env.detail.textContent, /<script>network failed<\/script>/);
  assert.match(env.detail.textContent, /按键/);
  await env.retry.onclick();
  assert.equal(env.retries(), 1);
  assert.equal(env.progress.hidden, false);
  assert.equal(env.retry.hidden, true);
});

test('retry failures remain explicit without an unhandled rejection', async () => {
  const env = environment({ retryError: true });
  await env.retry.onclick();
  await Promise.resolve();
  assert.equal(env.errors.length, 1);
  assert.equal(env.retry.hidden, false);
  assert.match(env.detail.textContent, /retry unavailable/);
});

test('the loading prompt can be dismissed without cancelling preparation', () => {
  const env = environment();
  env.notify('loading');
  env.close.onclick();
  assert.equal(env.panel.hidden, true);
  env.notify('ready');
  assert.equal(env.panel.hidden, true);
  assert.equal(env.retries(), 0);
});
