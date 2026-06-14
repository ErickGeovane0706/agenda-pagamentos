const CACHE = 'agenda-v1';

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE).then((cache) => cache.addAll([
      '/',
      '/lojas',
      '/agenda',
      '/relatorios',
      '/configuracoes',
      '/privacidade',
      '/manifest.json',
      '/icons/icon-192.png',
      '/icons/icon-512.png',
      '/icons/apple-touch-icon.png',
    ]))
  );
});

self.addEventListener('fetch', (event) => {
  if (event.request.url.includes('/api/')) return;
  event.respondWith(
    caches.match(event.request).then((cached) => cached || fetch(event.request))
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
    )
  );
});
