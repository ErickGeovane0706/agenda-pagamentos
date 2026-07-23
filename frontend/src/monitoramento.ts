import * as Sentry from '@sentry/react';

/**
 * Monitoramento de erro do frontend.
 *
 * Existe por causa do tráfego pago: um cadastro que quebra no celular do
 * cliente às 22h é invisível hoje — ele fecha a aba e nunca reclama. Sem isto,
 * a diferença entre "o anúncio não converteu" e "a tela estava quebrada" não
 * dá para saber.
 *
 * Mesmo escape hatch do Turnstile e do Resend: sem VITE_SENTRY_DSN nada é
 * iniciado e nenhuma requisição sai. É o que permite desenvolver e rodar local
 * sem conta, sem chave e sem poluir o painel com erro de teste.
 *
 * PII desligada de propósito. Com `sendDefaultPii: false` o SDK não anexa
 * cookies, cabeçalhos nem IP — o que chega ao Sentry é dado técnico de falha,
 * e não dado pessoal do cliente. Isso mantém a declaração da política de
 * privacidade simples e sustentável.
 *
 * ATENÇÃO: o envio depende da CSP (frontend/security-headers.conf) liberar o
 * host de ingestão. Sem isso o navegador bloqueia em silêncio e o painel fica
 * vazio parecendo que não há erro nenhum.
 */
export function iniciarMonitoramento() {
  const dsn = import.meta.env.VITE_SENTRY_DSN as string | undefined;
  if (!dsn) return;

  Sentry.init({
    dsn,
    environment: (import.meta.env.VITE_SENTRY_ENV as string) || import.meta.env.MODE,
    sendDefaultPii: false,

    // Só erro, sem performance. Rastreamento de transação multiplicaria o
    // consumo da cota gratuita sem responder a pergunta que interessa agora:
    // "quebrou para alguém?".
    tracesSampleRate: 0,

    // Ruído que não é falha nossa e só gasta cota: extensão de navegador,
    // requisição cancelada ao trocar de tela, e queda de rede do cliente.
    ignoreErrors: [
      'ResizeObserver loop limit exceeded',
      'Network Error',
      'Request aborted',
      'AbortError',
    ],

    beforeSend(evento) {
      // Cinto e suspensório: mesmo com sendDefaultPii falso, a URL pode
      // carregar o token do link de confirmação de cadastro ou de reset de
      // senha na query string. Token em painel de erro é credencial vazada.
      if (evento.request?.url) {
        evento.request.url = evento.request.url.replace(/token=[^&]+/g, 'token=REMOVIDO');
      }
      return evento;
    },
  });
}
