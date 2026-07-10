package com.agenda.domain.whatsappagent;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class WhatsAppSignatureValidatorTest {

    // Vetor conhecido de HMAC-SHA256: key="key", msg="The quick brown fox jumps over the lazy dog"
    private static final String SECRET = "key";
    private static final byte[] CORPO =
            "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);
    private static final String ASSINATURA_VALIDA =
            "sha256=f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8";

    @Test
    void assinaturaValida_DeveAceitarHmacCorreto() {
        var validator = new WhatsAppSignatureValidator(SECRET);
        assertTrue(validator.assinaturaValida(CORPO, ASSINATURA_VALIDA));
    }

    @Test
    void assinaturaValida_DeveRejeitarHmacErrado() {
        var validator = new WhatsAppSignatureValidator(SECRET);
        assertFalse(validator.assinaturaValida(CORPO, "sha256=" + "00".repeat(32)));
    }

    @Test
    void assinaturaValida_DeveRejeitarCorpoAdulterado() {
        var validator = new WhatsAppSignatureValidator(SECRET);
        byte[] adulterado = "The quick brown fox jumps over the lazy dog!".getBytes(StandardCharsets.UTF_8);
        assertFalse(validator.assinaturaValida(adulterado, ASSINATURA_VALIDA));
    }

    @Test
    void assinaturaValida_DeveRejeitarHeaderNuloOuSemPrefixo() {
        var validator = new WhatsAppSignatureValidator(SECRET);
        assertFalse(validator.assinaturaValida(CORPO, null));
        assertFalse(validator.assinaturaValida(CORPO, "f7bc83f4"));
    }

    @Test
    void isConfigurado_DependeDoAppSecret() {
        assertFalse(new WhatsAppSignatureValidator("").isConfigurado());
        assertFalse(new WhatsAppSignatureValidator("   ").isConfigurado());
        assertTrue(new WhatsAppSignatureValidator(SECRET).isConfigurado());
    }
}
