package com.agenda.domain.whatsappagent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Valida o header {@code X-Hub-Signature-256} dos webhooks da Meta:
 * HMAC-SHA256 do corpo cru da requisição usando o App Secret do app.
 *
 * Enquanto {@code WHATSAPP_APP_SECRET} não estiver configurado,
 * {@link #isConfigurado()} retorna false e o controller processa sem
 * validar (com aviso). Assim que o secret é definido, a validação passa
 * a ser obrigatória automaticamente.
 */
@Slf4j
@Component
public class WhatsAppSignatureValidator {

    private static final String PREFIXO = "sha256=";
    private final String appSecret;

    public WhatsAppSignatureValidator(@Value("${whatsapp.app-secret:}") String appSecret) {
        this.appSecret = appSecret;
    }

    public boolean isConfigurado() {
        return appSecret != null && !appSecret.isBlank();
    }

    /**
     * Retorna true se a assinatura do header confere com o HMAC do corpo.
     * Comparação em tempo constante para não vazar informação por timing.
     */
    public boolean assinaturaValida(byte[] corpo, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith(PREFIXO)) {
            return false;
        }
        String esperado = PREFIXO + hmacSha256Hex(corpo);
        return MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8));
    }

    private String hmacSha256Hex(byte[] corpo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(corpo);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            // Falha de config do algoritmo/chave — trata como assinatura inválida.
            log.error("[WHATSAPP-WEBHOOK] Erro ao calcular HMAC da assinatura: {}", e.getMessage());
            return "";
        }
    }
}
