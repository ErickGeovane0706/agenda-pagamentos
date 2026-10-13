import type { EventoFunil, Rastreador } from './tipos';

/**
 * Pixel da Meta.
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

const NOMES: Record<EventoFunil, string> = {
  cadastroEnviado: 'Lead',
  cadastroConfirmado: 'CompleteRegistration',
};

export function criarRastreadorMeta(id: string): Rastreador {
  return {
    carregar() {
      if (window.fbq) return;

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
      fbq('track', 'PageView');
    },
    registrar(evento) {
      window.fbq?.('track', NOMES[evento]);
    },
  };
}
