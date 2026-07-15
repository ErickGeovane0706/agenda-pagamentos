package com.agenda.domain.assinatura;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Recorte mínimo do payload de webhook do Asaas — só o que o sistema usa:
 * {@code id} do evento (idempotência), {@code event} (roteamento) e o
 * {@code customer} do pagamento (correlação com a assinatura).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasWebhookPayload(String id, String event, Payment payment) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payment(String customer) {}
}
