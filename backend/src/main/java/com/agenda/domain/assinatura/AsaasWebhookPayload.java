package com.agenda.domain.assinatura;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Recorte mínimo do payload de webhook do Asaas — só o que o sistema usa:
 * {@code id} do evento (idempotência), {@code event} (roteamento) e, do
 * pagamento, o {@code id} (uma vigência por pagamento — ver abaixo), o
 * {@code externalReference} (= empresaId, correlação preferida) e o
 * {@code customer} (correlação de fallback).
 * <p>
 * O {@code id} do <b>pagamento</b> não se confunde com o {@code id} do
 * <b>evento</b>: o Asaas dispara PAYMENT_CONFIRMED e PAYMENT_RECEIVED para o
 * mesmo pagamento de boleto, cada um com id de evento próprio. Deduplicar por
 * evento deixaria os dois passarem e creditaria dois meses por um pagamento.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasWebhookPayload(String id, String event, Payment payment) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payment(String id, String customer, String externalReference) {}
}
