const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'composeApp', 'src',
  'wasmJsMain', 'resources', 'startup.js'), 'utf8');

function environment() {
  const handlers = {};
  const timers = [];
  let reloads = 0;
  const elements = Object.fromEntries(['boot-status', 'boot-title', 'boot-detail', 'boot-retry']
    .map(id => [id, {
      hidden: true, textContent: '', attributes: {},
      setAttribute(name, value) { this.attributes[name] = value; },
      addEventListener(name, callback) { this[name] = callback; },
      remove() { this.removed = true; }
    }]));
  const window = {
    addEventListener: (name, callback) => { handlers[name] = callback; },
    location: { reload() { reloads++; } }
  };
  const context = {
    window, document: { getElementById: id => elements[id] },
    performance: { now: () => 10 },
    console: { info() {}, error() {} },
    setTimeout(callback, delay) {
      const timer = { callback, delay, cancelled: false };
      timers.push(timer);
      return timer;
    },
    clearTimeout(timer) { timer.cancelled = true; }
  };
  // Browser window properties are also global identifiers.
  window.showStartupError = undefined;
  vm.createContext(context);
  Object.defineProperty(context, 'showStartupError', { get: () => window.showStartupError });
  vm.runInContext(source, context);
  return { elements, window, timers, handlers, reloads: () => reloads };
}

test('startup stages are explicit and the ready callback removes the overlay', () => {
  const env = environment();
  assert.match(env.elements['boot-title'].textContent, /获取启动程序/);
  env.window.mentalMathStartup.programLoaded();
  assert.match(env.elements['boot-title'].textContent, /初始化运行引擎/);
  env.window.mentalMathStartup.rendering();
  assert.match(env.elements['boot-title'].textContent, /显示首页/);
  env.window.mentalMathStartup.programLoaded();
  assert.match(env.elements['boot-title'].textContent, /显示首页/);
  env.window.mentalMathStartup.ready();
  assert.equal(env.elements['boot-status'].removed, true);
  assert.equal(env.timers[0].cancelled, true);
  env.timers[0].callback();
  assert.equal(env.elements['boot-retry'].hidden, true);
});

test('startup timeout exposes a reload button without clearing site storage', () => {
  const env = environment();
  assert.equal(env.timers[0].delay, 60000);
  env.timers[0].callback();
  assert.equal(env.elements['boot-status'].attributes.role, 'alert');
  assert.match(env.elements['boot-detail'].textContent, /60 秒/);
  assert.equal(env.elements['boot-retry'].hidden, false);
  env.elements['boot-retry'].click();
  assert.equal(env.reloads(), 1);
});

test('startup failures remain visible while late success can still remove the overlay', () => {
  const env = environment();
  env.handlers.unhandledrejection({ reason: new Error('WASM unavailable') });
  env.window.mentalMathStartup.programLoaded();
  assert.match(env.elements['boot-detail'].textContent, /WASM unavailable/);
  env.window.mentalMathStartup.ready();
  assert.equal(env.elements['boot-status'].removed, true);
  env.handlers.error({ message: 'unrelated error after startup' });
  assert.equal(env.elements['boot-status'].removed, true);
});
