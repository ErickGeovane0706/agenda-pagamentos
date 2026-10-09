/**
 * Rastreamento do tráfego pago — o único ponto que as telas conhecem.
 *
 * Existe porque anúncio sem pixel roda cego: dá para saber quanto se gastou e
 * quantos cliques vieram, mas não quantos viraram conta. Pior que isso, sem
 * evento de conversão a plataforma não tem o que otimizar e distribui o
 * orçamento por cliques — a métrica errada para um funil que termina em
 * assinatura.
 *
 * As telas disparam `rastrear('cadastroConfirmado')` e não sabem quais
 * plataformas existem. Somar uma plataforma é uma implementação de `Rastreador`
 * e uma linha em `configuradas()`; nenhuma tela muda.
 *
 * Mesmo escape hatch do Sentry e do Turnstile: sem o id de uma plataforma ela
 * não carrega e nenhuma requisição sai para ela. É o que mantém o
 * desenvolvimento local sem conta de anúncios e sem sujar as métricas da
 * campanha com tráfego de teste.
 */
import { criarRastreadorMeta } from './meta';
import { criarRastreadorTikTok } from './tiktok';
import type { EventoFunil, Rastreador } from './tipos';

export type { EventoFunil } from './tipos';

/**
 * As telas de confirmar cadastro e de redefinir senha recebem o token na query
 * string. Os pixels mandam a URL inteira junto com cada visita e evento, e o
 * do TikTok ainda observa sozinho a navegação e as interações da página.
 * Token em painel de anúncio é credencial vazada para um terceiro que não tem
 * nada a ver com isso — mesma preocupação do `beforeSend` do Sentry
 * (src/monitoramento.ts). Resolvido pela raiz: com token na URL nenhum script
 * de terceiro carrega. Quem chegou por esse link passa a ser rastreado no
 * primeiro evento disparado já com a URL limpa (ver ConfirmarRegistroPage).
 */
function urlTemToken(url: string): boolean {
  return url.includes('token=');
}

export function criarRastreamento(rastreadores: Rastreador[], urlAtual: () => string) {
  let carregado = false;

  function carregarSePossivel(): boolean {
    if (carregado) return true;
    if (urlTemToken(urlAtual())) return false;
    rastreadores.forEach((r) => r.carregar());
    carregado = true;
    return true;
  }

  return {
    iniciar() {
      carregarSePossivel();
    },
    rastrear(evento: EventoFunil) {
      if (!carregarSePossivel()) return;
      rastreadores.forEach((r) => r.registrar(evento));
    },
  };
}

function configuradas(): Rastreador[] {
  const meta = import.meta.env.VITE_META_PIXEL_ID as string | undefined;
  const tiktok = import.meta.env.VITE_TIKTOK_PIXEL_ID as string | undefined;
  return [
    ...(meta ? [criarRastreadorMeta(meta)] : []),
    ...(tiktok ? [criarRastreadorTikTok(tiktok)] : []),
  ];
}

const rastreamento = criarRastreamento(configuradas(), () => window.location.href);

export const iniciarRastreamento = rastreamento.iniciar;
export const rastrear = rastreamento.rastrear;
