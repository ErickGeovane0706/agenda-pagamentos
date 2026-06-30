package com.agenda.domain.whatsappagent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Não chama a API real da Anthropic (sem rede em teste unitário). Testa
 * apenas o comportamento que não depende de rede: mensagem vazia/nula cai
 * direto em NAO_ENTENDIDO sem tentar chamar a API.
 * <p>
 * O parsing de JSON (incluindo blocos ```json e enums inválidos) é testado
 * indiretamente — ver {@link WhatsAppAgentServiceTest}, que mocka
 * {@code IntentClassifierService} inteiro. Um teste de integração real
 * (com chamada HTTP de verdade) deve ficar fora da suíte padrão, gated
 * por uma variável de ambiente, para não gastar créditos da API a cada
 * build.
 */
class IntentClassifierServiceTest {

    private final IntentClassifierService service = new IntentClassifierService();

    @Test
    void mensagemNula_naoChamaApiERetornaNaoEntendido() {
        ResultadoClassificacao resultado = service.classificar(null);

        assertEquals(IntencaoAgente.NAO_ENTENDIDO, resultado.getIntencao());
    }

    @Test
    void mensagemVazia_naoChamaApiERetornaNaoEntendido() {
        ResultadoClassificacao resultado = service.classificar("   ");

        assertEquals(IntencaoAgente.NAO_ENTENDIDO, resultado.getIntencao());
    }

    @Test
    void naoEntendido_temPeriodoSemFiltroPorPadrao() {
        ResultadoClassificacao resultado = ResultadoClassificacao.naoEntendido();

        assertEquals(IntencaoAgente.NAO_ENTENDIDO, resultado.getIntencao());
        assertEquals(TipoPeriodoAgente.SEM_FILTRO, resultado.getFiltros().getPeriodo());
    }
}
