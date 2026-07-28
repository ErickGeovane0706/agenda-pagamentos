// Trocar este nome invalida todo o cache anterior (o activate abaixo apaga os
// que não batem). A versão anterior era 'agenda-v1' FIXA, e como o activate só
// removia caches "diferentes do atual", o cache nunca era invalidado: o app
// ficava congelado na versão do primeiro install.
const CACHE = 'agenda-v2';

// Onde guardamos o HTML do app. É sempre o mesmo index.html para toda rota
// (o nginx faz try_files), então uma chave só basta — guardar por rota apenas
// multiplicava cópias do mesmo arquivo.
const SHELL = '/__app-shell';

// Assets do Vite carregam hash no nome (index-BgcgvS8m.js). O nome JÁ É a
// versão: mudou o conteúdo, mudou o nome. Por isso podem ser cache-first sem
// risco de servir coisa velha.
const IMUTAVEL = /\/assets\/[^/]+\.(js|css|woff2?)$/;

self.addEventListener('install', (event) => {
  // Assume no lugar do SW anterior sem esperar todas as abas fecharem. Sem
  // isto, um SW quebrado sobrevive a reinstalações e o usuário não tem como
  // sair do estado ruim sozinho.
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE).then((cache) => cache.addAll([
      '/manifest.json',
      '/icons/icon-192.png',
      '/icons/icon-512.png',
      '/icons/apple-touch-icon.png',
    ]))
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(
        keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))
      ))
      // Passa a controlar as abas já abertas nesta ativação, em vez de só nas
      // próximas. Junto com o skipWaiting, é o que faz a correção chegar sem
      // depender de o iOS despejar o storage por conta própria.
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const req = event.request;

  if (req.method !== 'GET') return;
  if (req.url.includes('/api/')) return;

  // NAVEGAÇÃO: rede primeiro, cache só como rede de segurança.
  //
  // Este é o ponto que causava tela em branco. O HTML aponta para o bundle com
  // o hash do build que o gerou; servir HTML do cache depois de um deploy pede
  // um /assets/index-<hash antigo>.js que não existe mais no servidor — 404, e
  // nada renderiza. Como o cache nunca era invalidado, não se curava sozinho.
  if (req.mode === 'navigate') {
    event.respondWith(
      fetch(req)
        .then((res) => {
          if (res.ok) {
            const copia = res.clone();
            caches.open(CACHE).then((cache) => cache.put(SHELL, copia));
          }
          return res;
        })
        // Sem rede E sem shell em cache (primeiro acesso offline), match devolve
        // undefined — e respondWith(undefined) estoura um erro obscuro em vez da
        // tela de "sem conexão" do navegador.
        .catch(() => caches.match(SHELL).then((cached) => cached || Response.error()))
    );
    return;
  }

  // ASSETS COM HASH: cache-first, que é seguro porque o nome muda a cada build.
  if (IMUTAVEL.test(req.url)) {
    event.respondWith(
      caches.match(req).then((cached) => cached || fetch(req).then((res) => {
        if (res.ok) {
          const copia = res.clone();
          caches.open(CACHE).then((cache) => cache.put(req, copia));
        }
        return res;
      }))
    );
    return;
  }

  // Resto (ícones, manifest): cache-first, sem cair se a rede falhar.
  event.respondWith(
    caches.match(req).then((cached) => cached || fetch(req))
  );
});
