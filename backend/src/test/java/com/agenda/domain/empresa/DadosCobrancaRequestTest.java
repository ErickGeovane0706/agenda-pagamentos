package com.agenda.domain.empresa;

import jakarta.validation.Validation;
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

    // --- Celular ---
    // O Asaas manda este campo como `mobilePhone` e exige celular de verdade.
    // Um fixo de 10 dígitos passava nas nossas duas validações e só era recusado
    // lá, com "Falha na comunicação com o gateway" — que não diz o que corrigir.

    private boolean celular(String telefone) {
        return new DadosCobrancaRequest("12345678909", telefone).isCelularValido();
    }

    @Test
    void celularComOnzeDigitos_DeveAceitar() {
        assertTrue(celular("83988887777"));
    }

    /**
     * O caso que derrubou a assinatura da empresa Tetse em 29/08. Vai pelo
     * validador inteiro, e não pelo isCelularValido, porque quem reprova
     * tamanho é o @Pattern — o que importa provar é que o record REJEITA o
     * número, não qual das duas anotações fez isso.
     */
    @Test
    void fixoDeDezDigitos_DeveRecusar() {
        assertFalse(aceito("8399999999"));
    }

    @Test
    void celularDeOnzeDigitos_DeveSerAceitoPeloValidador() {
        assertTrue(aceito("83988887777"));
    }

    private boolean aceito(String telefone) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator()
                .validate(new DadosCobrancaRequest("12345678909", telefone))
                .isEmpty();
        }
    }

    @Test
    void semONonoDigito_DeveRecusar() {
        assertFalse(celular("83388887777"));
    }

    @Test
    void dddInexistente_DeveRecusar() {
        assertFalse(celular("20999887766"));
    }

    @Test
    void dddValidoForaDoObvio_DeveAceitar() {
        assertTrue(celular("97999887766"));
    }

    /** Tamanho errado é problema do @Pattern, como no CPF. */
    @Test
    void celularComTamanhoInvalido_DeveDeixarPassar() {
        assertTrue(celular("123"));
    }
}
