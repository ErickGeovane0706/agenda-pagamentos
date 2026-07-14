package com.agenda.domain.whatsappagent;

import com.agenda.domain.webhook.WebhookIdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * JWT. A autenticação do POST é a validação do header
 * {@code X-Hub-Signature-256} (HMAC-SHA256 com o App Secret, ver
 * {@code receberEvento}); o GET usa o {@code hub.verify_token}.
 */
@Slf4j
@RestController
@RequestMapping("/webhook/whatsapp")
@RequiredArgsConstructor
public class WhatsAppWebhookController {

    private static final String ORIGEM = "WHATSAPP";

    private final WhatsAppAgentService agentService;
    private final WhatsAppAudioService audioService;
    private final WhatsAppSignatureValidator signatureValidator;
    private final WebhookIdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

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
     * Recebe eventos da Meta. Recebe o corpo cru ({@code byte[]}) porque a
     * validação da assinatura {@code X-Hub-Signature-256} (HMAC-SHA256 com o
     * App Secret) precisa dos bytes exatos, antes da desserialização.
     *
     * <ul>
     *   <li>Se o App Secret está configurado e a assinatura não confere → 401
     *       (rejeita chamada forjada).</li>
     *   <li>Se o App Secret ainda não foi configurado → processa com aviso
     *       (a validação passa a valer sozinha assim que o secret for definido).</li>
     * </ul>
     *
     * Cada mensagem é deduplicada por {@code message.id} (idempotência): uma
     * reentrega da Meta não dispara processamento (nem custo de IA) de novo.
     */
    @PostMapping
    public ResponseEntity<Void> receberEvento(
            @RequestBody byte[] corpo,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {

        if (signatureValidator.isConfigurado()) {
            if (!signatureValidator.assinaturaValida(corpo, signature)) {
                log.warn("[WHATSAPP-WEBHOOK] Assinatura X-Hub-Signature-256 inválida — rejeitando.");
                return ResponseEntity.status(401).build();
            }
        } else {
            log.warn("[WHATSAPP-WEBHOOK] WHATSAPP_APP_SECRET não configurado — assinatura NÃO validada. "
                    + "Configure o App Secret para fechar o webhook.");
        }

        try {
            var payload = objectMapper.readValue(corpo, WhatsAppWebhookPayload.class);
            extrairMensagensSuportadas(payload).forEach(this::processarSeNova);
        } catch (Exception e) {
            // Mesmo em erro de parsing/roteamento, respondemos 200 — devolver erro
            // para a Meta só causaria reenvios repetidos do mesmo payload malformado.
            log.error("[WHATSAPP-WEBHOOK] Erro ao processar payload recebido: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Processa a mensagem só se ainda não foi vista (dedup por {@code id}).
     * Sem {@code id} (payload atípico), processa — não há como deduplicar.
     * <p>
     * O dedup vem ANTES do roteamento por tipo de propósito: uma reentrega da
     * Meta de um áudio não pode disparar download + transcrição (ambos pagos)
     * de novo.
     */
    private void processarSeNova(WhatsAppWebhookPayload.IncomingMessage msg) {
        String id = msg.getId();
        if (id != null && !idempotencyService.registrarSeNovo(ORIGEM, id)) {
            log.info("[WHATSAPP-WEBHOOK] Mensagem duplicada ignorada: {}", id);
            return;
        }

        if (TIPO_TEXTO.equals(msg.getType())) {
            agentService.processarMensagem(msg.getFrom(), msg.getText().getBody());
        } else {
            // Já filtrado por suportada(): só sobra áudio.
            audioService.processarAudio(msg.getFrom(), msg.getAudio().getId());
        }
    }

    private List<WhatsAppWebhookPayload.IncomingMessage> extrairMensagensSuportadas(WhatsAppWebhookPayload payload) {
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
                .filter(this::suportada)
                .toList();
    }

    private static final String TIPO_TEXTO = "text";
    private static final String TIPO_AUDIO = "audio";

    /**
     * Texto e áudio são tratados; imagem, botão, localização, etc. ainda são
     * ignorados em silêncio (a Meta não reenvia por isso — só espera o 200).
     */
    private boolean suportada(WhatsAppWebhookPayload.IncomingMessage msg) {
        if (TIPO_TEXTO.equals(msg.getType())) {
            return msg.getText() != null && msg.getText().getBody() != null;
        }
        if (TIPO_AUDIO.equals(msg.getType())) {
            return msg.getAudio() != null && msg.getAudio().getId() != null;
        }
        return false;
    }
}
