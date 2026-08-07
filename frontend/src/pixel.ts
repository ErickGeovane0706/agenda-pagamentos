/**
 * Pixel da Meta — medição do tráfego pago.
 *
 * Existe porque anúncio sem pixel roda cego: dá para saber quanto se gastou e
 * quantos cliques vieram, mas não quantos viraram conta. Pior que isso, sem
 * evento de conversão a Meta não tem o que otimizar e distribui o orçamento
 * por cliques — que é a métrica errada para um funil que termina em assinatura.
 *
 * Mesmo escape hatch do Sentry e do Turnstile: sem VITE_META_PIXEL_ID nada é
 * carregado e nenhuma requisição sai para a Meta. É o que mantém o
 * desenvolvimento local sem conta de anúncios e sem sujar as métricas da
 * campanha com tráfego de teste.
 *
 * ATENÇÃO: depende da CSP (frontend/security-headers.conf) liberar
 * connect.facebook.net (o script) e www.facebook.com (para onde os eventos
 * vão). Sem isso o navegador bloqueia em silêncio e o painel fica vazio
 * parecendo que ninguém converteu — o mesmo desfecho traiçoeiro do Sentry.
 */

type Fbq = {
  (...args: unknown[]): void;
  callMethod?: (...args: unknown[]) => void;
  queue: unknown[][];
  push: unknown;
  loaded: boolean;
  version: string;
};

declare global {
  interface Window {
    fbq?: Fbq;
    _fbq?: Fbq;
  }
}

const SCRIPT_URL = 'https://connect.facebook.net/en_US/fbevents.js';

export function iniciarPixel(): void {
  const id = import.meta.env.VITE_META_PIXEL_ID as string | undefined;
  if (!id || window.fbq) return;

  // Fila-stub oficial da Meta. O fbevents.js é assíncrono, então um evento
  // disparado nos primeiros milissegundos chegaria antes do script existir;
  // a fila guarda a chamada e o script a reenvia quando carrega.
  const fbq = function (this: unknown, ...args: unknown[]) {
    if (fbq.callMethod) fbq.callMethod.apply(fbq, args);
    else fbq.queue.push(args);
  } as Fbq;
  fbq.queue = [];
  fbq.push = fbq;
  fbq.loaded = true;
  fbq.version = '2.0';
  window.fbq = fbq;
  window._fbq = fbq;

  const script = document.createElement('script');
  script.async = true;
  script.src = SCRIPT_URL;
  document.head.appendChild(script);

  fbq('init', id);

  // O PageView carrega a URL INTEIRA da página, query string incluída — e as
  // telas de confirmar cadastro e de redefinir senha recebem o token por ali.
  // Token em painel de anúncio é credencial vazada para um terceiro que não
  // tem nada a ver com isso. Mesma preocupação do `beforeSend` do Sentry
  // (src/monitoramento.ts), resolvida aqui pela raiz: nessas telas não se
  // rastreia visita. O evento de cadastro é disparado depois, já com a URL
  // limpa (ver ConfirmarRegistroPage).
  if (!window.location.search.includes('token=')) {
    fbq('track', 'PageView');
  }
}

/** Dispara um evento padrão da Meta. No-op quando o pixel não está configurado. */
export function pixelEvento(nome: string): void {
  window.fbq?.('track', nome);
}
