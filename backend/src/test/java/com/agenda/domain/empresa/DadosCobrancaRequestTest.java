package com.agenda.domain.empresa;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dígito verificador de CPF/CNPJ. Vale um teste próprio porque é a última
 * barreira antes do gateway — o que passa daqui vira uma ida à rede que falha
 * com mensagem genérica.
 */
class DadosCobrancaRequestTest {

    private boolean valido(String cpfCnpj) {
        return new DadosCobrancaRequest(cpfCnpj, "83988887777").isCpfCnpjValido();
    }

    @Test
    void cpfComDigitoCorreto_DeveAceitar() {
        assertTrue(valido("12345678909"));
    }

    @Test
    void cpfComDigitoErrado_DeveRecusar() {
        assertFalse(valido("12345678901"));
    }

    @Test
    void cpfComTodosDigitosIguais_DeveRecusar() {
        assertFalse(valido("11111111111"));
    }

    @Test
    void cnpjComDigitoCorreto_DeveAceitar() {
        assertTrue(valido("11222333000181"));
    }

    @Test
    void cnpjComDigitoErrado_DeveRecusar() {
        assertFalse(valido("11222333000199"));
    }

    /** Tamanho errado é problema do @Pattern — aqui passa para não duplicar a mensagem. */
    @Test
    void tamanhoInvalido_DeveDeixarPassar() {
        assertTrue(valido("123"));
    }
}
