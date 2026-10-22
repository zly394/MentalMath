const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources', 'sw.js'), 'utf8');

function worker(fetch) {
  const handlers = {};
  const stores = new Map();
  let skipWaiting = false;
  let claimed = false;
  const keyFor = request => new URL(typeof request === 'string' ? request : request.url, 'http://localhost/').href;
  const caches = {
    keys: async () => [...stores.keys()],
    delete: async key => stores.delete(key),
    open: async key => {
      if (!stores.has(key)) stores.set(key, new Map());
      const data = stores.get(key);
      return {
        addAll: async assets => {
          for (const asset of assets) data.set(keyFor(asset), new Response(asset));
        },
        put: async (request, response) => { data.set(keyFor(request), response); },
        match: async request => data.get(keyFor(request))
      };
    }
  };
  const self = {
    location: { origin: 'http://localhost' },
    addEventListener: (name, handler) => { handlers[name] = handler; },
    skipWaiting: () => { skipWaiting = true; return new Promise(() => {}); },
    clients: { claim: async () => { claimed = true; } }
  };
  vm.runInNewContext(source, { self, caches, fetch, URL, Response, console });
  return {
    caches, stores, skipped: () => skipWaiting, claimed: () => claimed,
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

test('installation precaches hashed WASM without waiting for worker activation', async () => {
  const env = worker(async () => new Response(JSON.stringify(['./index.html', './abc.wasm'])));
  await env.dispatch('install');
  assert.equal(env.skipped(), true);
  const cache = await env.caches.open('mentalmath-pwa-__BUILD_VERSION__');
  assert.equal(await (await cache.match('./abc.wasm')).text(), './abc.wasm');
});

test('activation removes old app caches but preserves unrelated caches', async () => {
  const env = worker();
  await env.caches.open('mentalmath-pwa-v2');
  await env.caches.open('another-app');
  await env.caches.open('mentalmath-pwa-__BUILD_VERSION__');
  await env.dispatch('activate');
  assert.deepEqual(await env.caches.keys(), ['another-app', 'mentalmath-pwa-__BUILD_VERSION__']);
  assert.equal(env.claimed(), true);
});

test('offline requests use the current app cache', async () => {
  const env = worker(async () => { throw new TypeError('Offline'); });
  const cache = await env.caches.open('mentalmath-pwa-__BUILD_VERSION__');
  await cache.put('./abc.wasm', new Response('wasm'));
  const response = await env.dispatch('fetch', new Request('http://localhost/abc.wasm'));
  assert.equal(await response.text(), 'wasm');
});

test('offline navigation with a query falls back to the precached index', async () => {
  const env = worker(async () => { throw new TypeError('Offline'); });
  const cache = await env.caches.open('mentalmath-pwa-__BUILD_VERSION__');
  await cache.put('./index.html', new Response('app'));
  const response = await env.dispatch('fetch', { url: 'http://localhost/?installed', method: 'GET', mode: 'navigate' });
  assert.equal(await response.text(), 'app');
});
