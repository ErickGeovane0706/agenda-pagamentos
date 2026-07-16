package com.agenda.domain.assinatura;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Recorte mínimo do payload de webhook do Asaas — só o que o sistema usa:
 * {@code id} do evento (idempotência), {@code event} (roteamento) e, do
 * pagamento, o {@code externalReference} (= empresaId, correlação preferida)
 * e o {@code customer} (correlação de fallback).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasWebhookPayload(String id, String event, Payment payment) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payment(String customer, String externalReference) {}
}
