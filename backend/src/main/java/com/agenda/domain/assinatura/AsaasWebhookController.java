package com.agenda.domain.assinatura;

import com.agenda.domain.webhook.WebhookIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Endpoint público que recebe os webhooks de cobrança do Asaas.
 * <p>
 * Mesmo padrão de segurança do webhook da Meta: valida a autenticação ANTES
 * de processar, deduplica por id de evento (idempotência — o Asaas reenvia
 * em caso de timeout) e responde 200 rápido. Depois de autenticado, responde
 * 200 MESMO em erro interno: o Asaas interrompe a fila de sincronização de
 * webhooks quando recebe não-200, o que travaria todos os eventos seguintes.
 * <p>
 * Autenticação: header {@code asaas-access-token}, comparado em tempo
 * constante com o token configurado (definido ao cadastrar o webhook no
 * painel do Asaas). Sem token configurado, recusa tudo — um webhook de
 * pagamento aberto ativaria assinaturas forjadas.
 * <p>
 * Rota fora de {@code /api/**}: cai no {@code anyRequest().permitAll()} do
 * {@code SecurityConfig} (sem JWT) e fora do {@code AssinaturaGateFilter}.
 */
@Slf4j
@RestController
@RequestMapping("/webhook/asaas")
@RequiredArgsConstructor
public class AsaasWebhookController {

    private static final String ORIGEM = "ASAAS";

    private final AssinaturaService assinaturaService;
    private final WebhookIdempotencyService idempotencyService;

    @Value("${asaas.webhook-token}")
    private String webhookToken;

    @PostMapping
    public ResponseEntity<Void> receberEvento(
            @RequestBody AsaasWebhookPayload payload,
            @RequestHeader(value = "asaas-access-token", required = false) String token) {

        if (webhookToken == null || webhookToken.isBlank()) {
            log.error("[ASAAS-WEBHOOK] ASAAS_WEBHOOK_TOKEN não configurado — recusando evento por segurança.");
            return ResponseEntity.status(500).build();
        }
        if (!tokenValido(token)) {
            log.warn("[ASAAS-WEBHOOK] Token de autenticação inválido — rejeitando.");
            return ResponseEntity.status(401).build();
        }

        try {
            processar(payload);
        } catch (Exception e) {
            log.error("[ASAAS-WEBHOOK] Erro ao processar evento {}: {}",
                    payload != null ? payload.id() : null, e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }

    private boolean tokenValido(String token) {
        return token != null && MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                webhookToken.getBytes(StandardCharsets.UTF_8));
    }

    private void processar(AsaasWebhookPayload payload) {
        if (payload.id() != null && !idempotencyService.registrarSeNovo(ORIGEM, payload.id())) {
            log.info("[ASAAS-WEBHOOK] Evento duplicado ignorado: {}", payload.id());
            return;
        }

        String customer = payload.payment() != null ? payload.payment().customer() : null;
        if (payload.event() == null || customer == null) {
            log.info("[ASAAS-WEBHOOK] Evento sem event/customer, ignorado: {}", payload.id());
            return;
        }

        switch (payload.event()) {
            case "PAYMENT_CONFIRMED", "PAYMENT_RECEIVED" -> {
                if (!assinaturaService.registrarPagamentoConfirmado(customer)) {
                    log.warn("[ASAAS-WEBHOOK] Pagamento do customer {} sem assinatura vinculada — "
                            + "vincular via PUT /api/assinaturas (gatewayCustomerId).", customer);
                }
            }
            case "PAYMENT_OVERDUE" -> {
                if (!assinaturaService.registrarInadimplencia(customer)) {
                    log.warn("[ASAAS-WEBHOOK] Cobrança vencida do customer {} sem assinatura vinculada.", customer);
                }
            }
            default -> log.debug("[ASAAS-WEBHOOK] Evento {} não tratado, ignorado.", payload.event());
        }
    }
}
