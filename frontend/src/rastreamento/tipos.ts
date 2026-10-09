/**
 * Os momentos do funil que interessam ao tráfego pago, no vocabulário do
 * produto. Cada plataforma traduz para o próprio nome de evento — e eles não
 * batem: o TikTok não tem `Lead`, o equivalente lá é `SubmitForm`. Por isso as
 * telas falam só destes nomes e nunca no de uma plataforma.
 */
export type EventoFunil =
  /** Formulário de cadastro enviado; o e-mail de confirmação ainda não foi clicado. */
  | 'cadastroEnviado'
  /** Conta criada de fato — é o evento que as campanhas otimizam. */
  | 'cadastroConfirmado';

/** Uma plataforma de anúncio. Para somar outra, basta outra implementação. */
export interface Rastreador {
  /** Injeta o script da plataforma e registra a visita à página atual. */
  carregar(): void;
  registrar(evento: EventoFunil): void;
}
