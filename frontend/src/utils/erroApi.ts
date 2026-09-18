import { AxiosError } from 'axios';

/**
 * Extrai a mensagem que o backend mandou, com um texto nosso de reserva.
 *
 * Existe porque o `ErrorResponse` do backend usa o campo `mensagem`, enquanto o
 * `useMutationToast` procura por `detail`/`title` — então a mensagem do
 * servidor nunca chega ao usuário por aquele caminho.
 *
 * No PDV isso não é detalhe: "Estoque insuficiente de Gelo 5kg (disponível: 2)"
 * diz exatamente o que fazer, e "ocorreu um erro inesperado" não diz nada a
 * quem está no caixa com o cliente esperando.
 */
export function mensagemDoErro(erro: unknown, reserva: string): string {
  const resposta = (erro as AxiosError<{ mensagem?: string }>)?.response;
  return resposta?.data?.mensagem?.trim() || reserva;
}
