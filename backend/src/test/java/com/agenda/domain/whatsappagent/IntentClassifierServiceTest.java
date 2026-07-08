package com.agenda.domain.whatsappagent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Não chama a API real da Anthropic (sem rede em teste unitário). Testa
 * o comportamento que não depende de rede: mensagem vazia/nula cai direto
 * em NAO_ENTENDIDO sem tentar chamar a API, e o parsing do JSON devolvido
 * pela LLM (via {@link IntentClassifierService#parsearResposta}, exposto
 * a nível de pacote só para isso) — incluindo os campos de período
 * "composto" (diaSemanaAlvo, posicaoQuinzena, diaDoMes, mesReferencia) que
 * o prompt manda a LLM sempre devolver. Um teste de integração real (com
 * chamada HTTP de verdade) deve ficar fora da suíte padrão, gated por uma
 * variável de ambiente, para não gastar créditos da API a cada build.
 */
class IntentClassifierServiceTest {

    private final IntentClassifierService service = new IntentClassifierService();

    @Test
    void mensagemNula_naoChamaApiERetornaNaoEntendido() {
        ResultadoClassificacao resultado = service.classificar(null, null);

        assertEquals(IntencaoAgente.NAO_ENTENDIDO, resultado.getIntencao());
    }

    @Test
    void mensagemVazia_naoChamaApiERetornaNaoEntendido() {
        ResultadoClassificacao resultado = service.classificar("   ", null);

        assertEquals(IntencaoAgente.NAO_ENTENDIDO, resultado.getIntencao());
    }

    @Test
    void naoEntendido_temPeriodoSemFiltroPorPadrao() {
        ResultadoClassificacao resultado = ResultadoClassificacao.naoEntendido();

        assertEquals(IntencaoAgente.NAO_ENTENDIDO, resultado.getIntencao());
        assertEquals(TipoPeriodoAgente.SEM_FILTRO, resultado.getFiltros().getPeriodo());
    }

    // ---------------------------------------------------------------
    // Regressão: o prompt passou a mandar a LLM sempre devolver
    // diaSemanaAlvo/posicaoQuinzena/diaDoMes/mesReferencia no JSON, mas o
    // DTO de parsing não tinha esses campos — com o ObjectMapper padrão
    // (FAIL_ON_UNKNOWN_PROPERTIES=true), isso derrubava a desserialização
    // de TODA resposta da LLM, não só as que usam os períodos novos.
    // ---------------------------------------------------------------

    @Test
    void respostaComCamposDePeriodoComposto_naoQuebraOParsing() throws Exception {
        String json = """
                {"intencao":"CONSULTAR_PENDENCIAS","filtros":{"loja":null,"tipoPagamento":null,
                "periodo":"ATE_DIA_SEMANA","diaSemanaAlvo":"SEGUNDA","posicaoQuinzena":null,
                "diaDoMes":null,"mesReferencia":null,"dataInicio":null,"dataFim":null,
                "fornecedor":null,"filtroStatus":"PENDENTE"}}
                """;

        ResultadoClassificacao resultado = service.parsearResposta(json);

        assertEquals(IntencaoAgente.CONSULTAR_PENDENCIAS, resultado.getIntencao());
        assertEquals(TipoPeriodoAgente.ATE_DIA_SEMANA, resultado.getFiltros().getPeriodo());
        assertEquals(DiaSemanaAgente.SEGUNDA, resultado.getFiltros().getDiaSemanaAlvo());
    }

    @Test
    void respostaComDiaDoMes_preencheOCampoNumerico() throws Exception {
        String json = """
                {"intencao":"CONSULTAR_PENDENCIAS","filtros":{"loja":null,"tipoPagamento":null,
                "periodo":"DIA_DO_MES","diaSemanaAlvo":null,"posicaoQuinzena":null,
                "diaDoMes":15,"mesReferencia":"ATUAL","dataInicio":null,"dataFim":null,
                "fornecedor":null,"filtroStatus":"PENDENTE"}}
                """;

        ResultadoClassificacao resultado = service.parsearResposta(json);

        assertEquals(15, resultado.getFiltros().getDiaDoMes());
        assertEquals(MesReferenciaAgente.ATUAL, resultado.getFiltros().getMesReferencia());
    }

    @Test
    void intencaoRefinarUltimaConsulta_eReconhecida() throws Exception {
        String json = """
                {"intencao":"REFINAR_ULTIMA_CONSULTA","filtros":{"loja":"centro","tipoPagamento":null,
                "periodo":"SEM_FILTRO","diaSemanaAlvo":null,"posicaoQuinzena":null,"diaDoMes":null,
                "mesReferencia":null,"dataInicio":null,"dataFim":null,"fornecedor":null,
                "filtroStatus":"PENDENTE"}}
                """;

        ResultadoClassificacao resultado = service.parsearResposta(json);

        assertEquals(IntencaoAgente.REFINAR_ULTIMA_CONSULTA, resultado.getIntencao());
    }

    @Test
    void campoDesconhecidoNaoPrevistoNoDto_eIgnoradoSemQuebrar() throws Exception {
        String json = """
                {"intencao":"HOJE_INEXISTENTE_NO_ENUM","umCampoQueNuncaVaiExistirNoDto":"x",
                "filtros":{"loja":null,"tipoPagamento":null,"periodo":"HOJE"}}
                """;

        ResultadoClassificacao resultado = service.parsearResposta(json);

        assertEquals(IntencaoAgente.NAO_ENTENDIDO, resultado.getIntencao());
    }
}
