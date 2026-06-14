package com.agenda.domain.usuario;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordValidatorTest {

    private final PasswordValidator validator = new PasswordValidator();

    @Test
    void validar_DeveAceitarSenhaForte() {
        assertDoesNotThrow(() -> validator.validar("Senha123"));
    }

    @Test
    void validar_DeveRejeitarSenhaCurta() {
        var ex = assertThrows(IllegalArgumentException.class, () -> validator.validar("Ab1"));
        assertTrue(ex.getMessage().contains("8 caracteres"));
    }

    @Test
    void validar_DeveRejeitarSemMaiuscula() {
        var ex = assertThrows(IllegalArgumentException.class, () -> validator.validar("senha1234"));
        assertTrue(ex.getMessage().contains("maiúscula"));
    }

    @Test
    void validar_DeveRejeitarSemMinuscula() {
        var ex = assertThrows(IllegalArgumentException.class, () -> validator.validar("SENHA1234"));
        assertTrue(ex.getMessage().contains("minúscula"));
    }

    @Test
    void validar_DeveRejeitarSemNumero() {
        var ex = assertThrows(IllegalArgumentException.class, () -> validator.validar("SenhaForte"));
        assertTrue(ex.getMessage().contains("número"));
    }

    @Test
    void validar_DeveRejeitarNull() {
        assertThrows(IllegalArgumentException.class, () -> validator.validar(null));
    }
}
