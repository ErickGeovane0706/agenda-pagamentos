package com.agenda.domain.assinatura;

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
 * Mesmo padrão de segurança do webhook da Meta: valida a autenticação ANTES de
 * processar e responde rápido. O efeito em si fica no {@link AsaasWebhookProcessor},
 * que roda numa transação — aqui só ficam autenticação e a tradução de falha em
 * status HTTP.
 * <p>
 * Autenticação: header {@code asaas-access-token}, comparado em tempo constante
 * com o token configurado (definido ao cadastrar o webhook no painel do Asaas).
 * Sem token configurado, recusa tudo — um webhook de pagamento aberto ativaria
 * assinaturas forjadas.
 * <p>
 * <b>Sobre o status de resposta:</b> o Asaas interrompe a fila de webhooks ao
 * receber não-200, então respondemos 200 em tudo que reenviar não resolveria
 * (evento desconhecido, empresa inexistente, payload incompleto). Mas falha
 * transitória — tipicamente banco fora — devolve 500 de propósito: pausar a fila
 * e receber o evento de novo é melhor que perder um pagamento confirmado, que é
 * irrecuperável (o Asaas não reenvia o que já foi respondido com 200).
 * <p>
 * Rota fora de {@code /api/**}: cai no {@code anyRequest().permitAll()} do
 * {@code SecurityConfig} (sem JWT) e fora do {@code AssinaturaGateFilter}.
 */
@Slf4j
@RestController
@RequestMapping("/webhook/asaas")
@RequiredArgsConstructor
public class AsaasWebhookController {

    private final AsaasWebhookProcessor processor;

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
            processor.processar(payload);
        } catch (Exception e) {
            log.error("[ASAAS-WEBHOOK] Falha ao processar evento {} — respondendo 500 para o Asaas reenviar: {}",
                    payload != null ? payload.id() : null, e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
        return ResponseEntity.ok().build();
    }

    private boolean tokenValido(String token) {
        return token != null && MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                webhookToken.getBytes(StandardCharsets.UTF_8));
    }
}
