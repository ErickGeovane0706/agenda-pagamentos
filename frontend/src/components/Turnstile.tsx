import { useEffect, useRef } from 'react';

/**
 * Widget anti-bot do Cloudflare Turnstile.
 *
 * Escape hatch espelhando o backend: sem VITE_TURNSTILE_SITE_KEY, o widget nem
 * renderiza e o onToken é chamado com string vazia — o backend, também sem
 * secret, aceita. É o que permite desenvolver e testar o cadastro antes de
 * existir domínio próprio (a Cloudflare recusa hostname em up.railway.app).
 *
 * O script é carregado sob demanda, só quando o cadastro é aberto, para não
 * pesar nas outras telas nem contatar a Cloudflare sem necessidade.
 */

declare global {
  interface Window {
    turnstile?: {
      render: (el: HTMLElement, opts: TurnstileOptions) => string;
      remove: (id: string) => void;
    };
  }
}

interface TurnstileOptions {
  sitekey: string;
  callback: (token: string) => void;
  'error-callback'?: () => void;
  'expired-callback'?: () => void;
  theme?: 'light' | 'dark' | 'auto';
}

const SCRIPT_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js';
const SITE_KEY = import.meta.env.VITE_TURNSTILE_SITE_KEY as string | undefined;

function carregarScript(): Promise<void> {
  if (window.turnstile) return Promise.resolve();
  const existente = document.querySelector<HTMLScriptElement>(`script[src="${SCRIPT_SRC}"]`);
  if (existente) {
    return new Promise((res) => existente.addEventListener('load', () => res()));
  }
  return new Promise((res, rej) => {
    const s = document.createElement('script');
    s.src = SCRIPT_SRC;
    s.async = true;
    s.defer = true;
    s.onload = () => res();
    s.onerror = () => rej();
    document.head.appendChild(s);
  });
}

export default function Turnstile({ onToken }: { onToken: (token: string) => void }) {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // Sem site key (dev sem domínio): libera o fluxo com token vazio. O backend
    // valida, e sem secret ele deixa passar com aviso no log.
    if (!SITE_KEY) {
      onToken('');
      return;
    }

    let widgetId: string | undefined;
    let cancelado = false;

    carregarScript()
      .then(() => {
        if (cancelado || !containerRef.current || !window.turnstile) return;
        widgetId = window.turnstile.render(containerRef.current, {
          sitekey: SITE_KEY,
          callback: onToken,
          // Token que expira volta a barrar o envio até resolver de novo.
          'expired-callback': () => onToken(''),
          'error-callback': () => onToken(''),
          theme: 'light',
        });
      })
      .catch(() => {
        // Script não carregou: não trava o cadastro. Token vazio segue para o
        // backend decidir (com secret configurada, ele recusa; sem, aceita).
        onToken('');
      });

    return () => {
      cancelado = true;
      if (widgetId && window.turnstile) window.turnstile.remove(widgetId);
    };
  }, [onToken]);

  if (!SITE_KEY) {
    return (
      <div className="reg-turnstile-nota">
        <span>✓</span> Verificação de segurança ativa em produção
      </div>
    );
  }

  return <div className="reg-turnstile" ref={containerRef} />;
}
