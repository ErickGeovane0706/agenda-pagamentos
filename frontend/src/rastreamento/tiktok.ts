import type { EventoFunil, Rastreador } from './tipos';

/**
 * Pixel do TikTok.
 *
 * ATENÇÃO: depende da CSP (frontend/security-headers.conf) liberar
 * analytics.tiktok.com em script-src (o events.js e o main.*.js que ele puxa),
 * connect-src (os eventos vão por sendBeacon) e img-src (o fallback quando o
 * beacon falha), mais analytics-ipv6.tiktokw.us em connect-src. Hosts lidos do
 * próprio SDK em 09/10/2026 — se o TikTok mudar, o painel fica vazio em silêncio.
 */

type Fila = unknown[] & Record<string, unknown>;

type Ttq = Fila & {
  methods: string[];
  _i: Record<string, Fila>;
  _t: Record<string, number>;
  _o: Record<string, Record<string, unknown>>;
  load: (id: string) => void;
  page: () => void;
  track: (evento: string) => void;
};

declare global {
  interface Window {
    ttq?: Ttq;
    TiktokAnalyticsObject?: string;
  }
}

const SCRIPT_URL = 'https://analytics.tiktok.com/i18n/pixel/events.js';

// Lista do snippet oficial. O events.js lê a fila esperando estes métodos.
const METODOS = [
  'page', 'track', 'identify', 'instances', 'debug', 'on', 'off', 'once', 'ready',
  'alias', 'group', 'enableCookie', 'disableCookie', 'holdConsent', 'revokeConsent', 'grantConsent',
];

// Nomes conferidos na lista de eventos padrão do TikTok (ads.tiktok.com/help,
// aid=10028). Não existe `Lead` lá — `SubmitForm` é o equivalente.
const NOMES: Record<EventoFunil, string> = {
  cadastroEnviado: 'SubmitForm',
  cadastroConfirmado: 'CompleteRegistration',
};

/** Cada método vira "empilhe [método, ...args]" até o SDK chegar e consumir a fila. */
function adiar(alvo: Fila, metodo: string) {
  alvo[metodo] = (...args: unknown[]) => {
    alvo.push([metodo, ...args]);
  };
}

export function criarRastreadorTikTok(id: string): Rastreador {
  return {
    carregar() {
      if (window.ttq) return;

      // Tradução do snippet oficial do TikTok, sem a minificação.
      window.TiktokAnalyticsObject = 'ttq';
      const ttq = [] as unknown as Ttq;
      ttq.methods = METODOS;
      METODOS.forEach((metodo) => adiar(ttq, metodo));
      ttq.instance = (instancia: string) => {
        const fila = ttq._i[instancia] || ([] as unknown as Fila);
        METODOS.forEach((metodo) => adiar(fila, metodo));
        return fila;
      };
      ttq.load = (pixel: string) => {
        ttq._i = ttq._i || {};
        ttq._i[pixel] = Object.assign([], { _u: SCRIPT_URL }) as unknown as Fila;
        ttq._t = ttq._t || {};
        ttq._t[pixel] = Date.now();
        ttq._o = ttq._o || {};
        ttq._o[pixel] = {};

        const script = document.createElement('script');
        script.async = true;
        script.src = `${SCRIPT_URL}?sdkid=${encodeURIComponent(pixel)}&lib=ttq`;
        document.head.appendChild(script);
      };
      window.ttq = ttq;

      ttq.load(id);
      ttq.page();
    },
    registrar(evento) {
      window.ttq?.track(NOMES[evento]);
    },
  };
}
