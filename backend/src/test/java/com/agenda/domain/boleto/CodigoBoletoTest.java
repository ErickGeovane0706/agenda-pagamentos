package com.agenda.domain.boleto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * O mesmo boleto chega em três formas (câmera = 44, papel = 47 ou 48, digitado à
 * mão com pontos e espaços). Para detectar duplicata, todas precisam virar a mesma
 * chave: os 44 dígitos do código de barras.
 * <p>
 * Par 44/47 é o exemplo de boleto do Banco do Brasil (layout FEBRABAN): a linha
 * digitável reordena o código em campos e acrescenta um DV em cada um dos três
 * primeiros. Na arrecadação (48), são quatro blocos de 11 dígitos + 1 DV.
 */
class CodigoBoletoTest {

    private static final String BARRAS_BANCARIO = "00193373700000001000500940144816060680935031";
    private static final String LINHA_BANCARIO = "00190.50095 40144.816069 06809.350314 3 37370000000100";

    @Test
    void codigoDeBarrasDe44FicaComoEsta() {
        assertEquals(BARRAS_BANCARIO, CodigoBoleto.normalizar(BARRAS_BANCARIO));
    }

    @Test
    void linhaDigitavelBancariaVoltaAoCodigoDeBarras() {
        assertEquals(BARRAS_BANCARIO, CodigoBoleto.normalizar(LINHA_BANCARIO));
    }

    @Test
    void linhaDeArrecadacaoPerdeUmDvPorBloco() {
        // O normalizador não confere DV (isso é do leitor no front), então os DVs
        // 1, 2, 3 e 4 abaixo só marcam a posição que precisa sumir.
        String barras = "83620000000" + "66780048100" + "01234567890" + "12345678901";
        String linha = "83620000000-1 66780048100-2 01234567890-3 12345678901-4";
        assertEquals(barras, CodigoBoleto.normalizar(linha));
    }

    @Test
    void comprimentoQueNaoEBoletoNaoViraChave() {
        assertNull(CodigoBoleto.normalizar(null));
        assertNull(CodigoBoleto.normalizar("   "));
        assertNull(CodigoBoleto.normalizar("12345678901"));
        assertNull(CodigoBoleto.normalizar(BARRAS_BANCARIO.substring(1)));
    }
}
