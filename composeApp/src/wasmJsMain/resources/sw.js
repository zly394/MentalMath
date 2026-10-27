const CACHE_NAME = 'mentalmath-pwa-__BUILD_VERSION__';

self.addEventListener('install', event => {
  event.waitUntil((async () => {
    const response = await fetch('./pwa-assets.json', { cache: 'no-store' });
    if (!response.ok) throw new Error(`Asset manifest: HTTP ${response.status}`);
    const assets = await response.json();
    const cache = await caches.open(CACHE_NAME);
    await cache.addAll(assets.map(asset => new Request(asset, { cache: 'reload' })));
  })());
  // Wait for existing pages to close so an app never mixes release resources.
});

self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(
      keys.filter(key => key.startsWith('mentalmath-pwa-') && key !== CACHE_NAME)
        .map(key => caches.delete(key))
    );
  })());
});

self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET' || url.origin !== self.location.origin) return;
  if (url.pathname.includes('/__speech_diagnostics/')) return;
  // Vosk persists the extracted model in IndexedDB; avoid storing another 42 MiB copy.
  if (url.pathname.endsWith('/vosk-model-cn.tar.gz')) return;
  event.respondWith((async () => {
    const cache = await caches.open(CACHE_NAME);
    const cached = await cache.match(event.request);
    if (cached) return cached;
    if (event.request.mode === 'navigate') {
      const index = await cache.match('./index.html');
      if (index) return index;
    }
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 15000);
    try {
      const response = await fetch(event.request, { signal: controller.signal });
      if (response.ok) {
        await cache.put(event.request, response.clone());
      }
      return response;
    } catch (error) {
      console.error('Offline resource unavailable:', event.request.url, error);
      return Response.error();
    } finally {
      clearTimeout(timeout);
    }
  })());
});
