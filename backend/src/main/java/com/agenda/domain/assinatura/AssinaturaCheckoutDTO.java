package com.agenda.domain.assinatura;

/**
 * Resultado de iniciar uma assinatura no gateway: o id da subscription e a URL
 * da página de pagamento (Pix/boleto) para onde o cliente é redirecionado.
 * {@code urlPagamento} pode vir null se a primeira cobrança ainda não foi gerada.
 */
public record AssinaturaCheckoutDTO(String subscriptionId, String urlPagamento) {
}
