const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources', 'sw.js'), 'utf8');

function worker(fetch, { manualTimers = false } = {}) {
  const handlers = {};
  const stores = new Map();
  let skipWaiting = false;
  let claimed = false;
  const timers = [];
  const keyFor = request => new URL(typeof request === 'string' ? request : request.url, 'http://localhost/').href;
  const caches = {
    keys: async () => [...stores.keys()],
    delete: async key => stores.delete(key),
    open: async key => {
      if (!stores.has(key)) stores.set(key, new Map());
      const data = stores.get(key);
      return {
        addAll: async assets => {
          for (const asset of assets) {
            assert.equal(asset.cache, 'reload');
            data.set(keyFor(asset), new Response(asset.url));
          }
        },
        put: async (request, response) => { data.set(keyFor(request), response); },
        match: async request => data.get(keyFor(request))?.clone()
      };
    }
  };
  const self = {
    location: { origin: 'http://localhost' },
    addEventListener: (name, handler) => { handlers[name] = handler; },
    skipWaiting: () => { skipWaiting = true; return new Promise(() => {}); },
    clients: { claim: async () => { claimed = true; } }
  };
  vm.runInNewContext(source, {
    self, caches, fetch, URL, Response, console, AbortController,
    Request: class extends Request {
      constructor(url, options) { super(new URL(url, 'http://localhost/'), options); }
    },
    setTimeout: manualTimers ? (callback, delay) => {
      const timer = { callback, delay };
      timers.push(timer);
      return timer;
    } : setTimeout,
    clearTimeout: manualTimers ? () => {} : clearTimeout
  });
  return {
    caches, stores, timers, skipped: () => skipWaiting, claimed: () => claimed,
    dispatch: async (name, request) => {
      let result;
      handlers[name]({
        request,
        waitUntil: promise => { result = promise; },
        respondWith: promise => { result = promise; }
      });
      return result;
    }
  };
}

test('installation precaches a complete release without forcing existing clients to upgrade', async () => {
  const env = worker(async () => new Response(JSON.stringify(['./index.html', './abc.wasm'])));
  await env.dispatch('install');
  assert.equal(env.skipped(), false);
  const cache = await env.caches.open('mentalmath-pwa-__BUILD_VERSION__');
  assert.equal(await (await cache.match('./abc.wasm')).text(), 'http://localhost/abc.wasm');
});

test('activation removes old app caches but preserves unrelated caches', async () => {
  const env = worker();
  await env.caches.open('mentalmath-pwa-v2');
  await env.caches.open('another-app');
  await env.caches.open('mentalmath-pwa-__BUILD_VERSION__');
  await env.dispatch('activate');
  assert.deepEqual(await env.caches.keys(), ['another-app', 'mentalmath-pwa-__BUILD_VERSION__']);
  assert.equal(env.claimed(), false);
});

test('offline requests use the current app cache', async () => {
  const env = worker(async () => { throw new TypeError('Offline'); });
  const cache = await env.caches.open('mentalmath-pwa-__BUILD_VERSION__');
  await cache.put('./abc.wasm', new Response('wasm'));
  const response = await env.dispatch('fetch', new Request('http://localhost/abc.wasm'));
  assert.equal(await response.text(), 'wasm');
});

test('model archives bypass the app cache because Vosk owns their IndexedDB storage', async () => {
  const env = worker(() => { throw new Error('Must not intercept model downloads'); });
  assert.equal(await env.dispatch('fetch', new Request('http://localhost/MentalMath/vosk-model-cn.tar.gz')), undefined);
  assert.equal(env.stores.size, 0);
});

test('offline navigation with a query falls back to the precached index', async () => {
  const env = worker(async () => { throw new TypeError('Offline'); });
  const cache = await env.caches.open('mentalmath-pwa-__BUILD_VERSION__');
  await cache.put('./index.html', new Response('app'));
  const response = await env.dispatch('fetch', { url: 'http://localhost/?installed', method: 'GET', mode: 'navigate' });
  assert.equal(await response.text(), 'app');
});

test('cached startup assets and navigation return without waiting for a hanging network', async () => {
  let requests = 0;
  const env = worker(() => { requests++; return new Promise(() => {}); });
  const cache = await env.caches.open('mentalmath-pwa-__BUILD_VERSION__');
  for (const asset of ['mentalmath.js', 'abc.wasm', 'index.html', 'speech.js']) {
    await cache.put('./' + asset, new Response('cached-' + asset));
  }
  for (const asset of ['mentalmath.js', 'abc.wasm', 'speech.js']) {
    assert.equal(await (await env.dispatch('fetch', new Request('http://localhost/' + asset))).text(),
      'cached-' + asset);
  }
  const navigation = await env.dispatch('fetch', {
    url: 'http://localhost/?installed', method: 'GET', mode: 'navigate'
  });
  assert.equal(await navigation.text(), 'cached-index.html');
  assert.equal(requests, 0);
});

test('a release cache is not overwritten by unversioned resources from a newer deployment', async () => {
  const env = worker(async () => new Response('new-release'));
  const cache = await env.caches.open('mentalmath-pwa-__BUILD_VERSION__');
  await cache.put('./mentalmath.js', new Response('old-release'));
  assert.equal(await (await env.dispatch('fetch', new Request('http://localhost/mentalmath.js'))).text(),
    'old-release');
  assert.equal(await (await cache.match('./mentalmath.js')).text(), 'old-release');
});

test('uncached network requests abort after 15 seconds instead of blocking startup indefinitely', async () => {
  const env = worker((request, { signal }) => new Promise((resolve, reject) => {
    signal.addEventListener('abort', () => reject(new Error('Request timed out')));
  }), { manualTimers: true });
  const response = env.dispatch('fetch', new Request('http://localhost/missing.wasm'));
  while (env.timers.length === 0) await Promise.resolve();
  assert.equal(env.timers[0].delay, 15000);
  env.timers[0].callback();
  assert.equal((await response).type, 'error');
});

test('uncached successful resources are retained for subsequent offline requests', async () => {
  let requests = 0;
  const env = worker(async () => { requests++; return new Response('downloaded'); });
  for (let attempt = 0; attempt < 2; attempt++) {
    assert.equal(await (await env.dispatch('fetch', new Request('http://localhost/extra.js'))).text(), 'downloaded');
  }
  assert.equal(requests, 1);
});
