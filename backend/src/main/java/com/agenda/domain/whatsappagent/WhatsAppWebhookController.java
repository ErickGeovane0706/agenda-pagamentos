package com.agenda.domain.whatsappagent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoint público que recebe os webhooks da Meta Cloud API.
 * <p>
 * Dois fluxos distintos, exigidos pela própria Meta:
 * <ul>
 *   <li>{@code GET /webhook/whatsapp}: verificação inicial feita pela Meta
 *       quando você cadastra a URL no painel — precisa ecoar o
 *       {@code hub.challenge} de volta SE o {@code hub.verify_token}
 *       bater com o token configurado.</li>
 *   <li>{@code POST /webhook/whatsapp}: notificação de evento real
 *       (mensagem recebida, status de entrega, etc). Responde 200
 *       imediatamente e delega o processamento pesado (LLM + banco)
 *       de forma assíncrona — a Meta espera resposta em poucos segundos
 *       e reenvia o webhook se não receber 200 a tempo.</li>
 * </ul>
 * <p>
 * Rota fora de {@code /api/**}, então cai na regra
 * {@code anyRequest().permitAll()} do {@code SecurityConfig} — não exige
 * JWT. A "autenticação" deste endpoint é o {@code hub.verify_token} (na
 * configuração) e, em produção, deveria também validar o header
 * {@code X-Hub-Signature-256} (ver nota no método {@code receberEvento}).
 */
@Slf4j
@RestController
@RequestMapping("/webhook/whatsapp")
@RequiredArgsConstructor
public class WhatsAppWebhookController {

    private final WhatsAppAgentService agentService;

    @Value("${whatsapp.webhook-verify-token}")
    private String verifyToken;

    @GetMapping
    public ResponseEntity<String> verificarWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge
    ) {
        if (verifyToken == null || verifyToken.isBlank()) {
            log.error("[WHATSAPP-WEBHOOK] WHATSAPP_WEBHOOK_VERIFY_TOKEN não configurado — recusando verificação por segurança.");
            return ResponseEntity.status(500).build();
        }
        if ("subscribe".equals(mode) && verifyToken.equals(token)) {
            log.info("[WHATSAPP-WEBHOOK] Verificação de webhook bem-sucedida.");
            return ResponseEntity.ok(challenge);
        }
        log.warn("[WHATSAPP-WEBHOOK] Tentativa de verificação com token inválido.");
        return ResponseEntity.status(403).build();
    }

    /**
     * TODO antes de produção: validar o header {@code X-Hub-Signature-256}
     * (HMAC-SHA256 do corpo da requisição com o App Secret da Meta) para
     * garantir que a chamada realmente vem da Meta, e não de um terceiro
     * que descobriu esta URL. Requer acesso ao corpo raw da requisição
     * antes da desserialização — normalmente implementado com um
     * {@code Filter} dedicado, não dentro do controller.
     */
    @PostMapping
    public ResponseEntity<Void> receberEvento(@RequestBody WhatsAppWebhookPayload payload) {
        try {
            extrairMensagensDeTexto(payload).forEach(msg ->
                    agentService.processarMensagem(msg.getFrom(), msg.getText().getBody()));
        } catch (Exception e) {
            // Mesmo em erro de parsing/roteamento, respondemos 200 — devolver erro
            // para a Meta só causaria reenvios repetidos do mesmo payload malformado.
            log.error("[WHATSAPP-WEBHOOK] Erro ao processar payload recebido: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }

    private List<WhatsAppWebhookPayload.IncomingMessage> extrairMensagensDeTexto(WhatsAppWebhookPayload payload) {
        if (payload.getEntry() == null) {
            return List.of();
        }
        return payload.getEntry().stream()
                .filter(entry -> entry.getChanges() != null)
                .flatMap(entry -> entry.getChanges().stream())
                .filter(change -> "messages".equals(change.getField()) && change.getValue() != null)
                .map(WhatsAppWebhookPayload.Change::getValue)
                .filter(value -> value.getMessages() != null)
                .flatMap(value -> value.getMessages().stream())
                // Só "text" é suportado na v1 — áudio/imagem/botão são ignorados
                // (poderiam, no futuro, virar uma etapa de transcrição antes do classificador).
                .filter(msg -> "text".equals(msg.getType()) && msg.getText() != null && msg.getText().getBody() != null)
                .toList();
    }
}
