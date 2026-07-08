package com.agenda.domain.whatsappagent;

/**
 * Como o cliente delimitou o período da consulta. {@code INTERVALO} exige
 * que {@link FiltrosAgente#getDataInicio()} e {@link FiltrosAgente#getDataFim()}
 * estejam preenchidas; os demais valores são resolvidos em código (nunca
 * pela LLM) a partir da data corrente — ver
 * {@link WhatsAppAgentService#resolverPeriodo}.
 * <p>
 * {@code ATE_DIA_SEMANA} exige {@link FiltrosAgente#getDiaSemanaAlvo()}.
 * {@code QUINZENA} pode opcionalmente ter {@link FiltrosAgente#getPosicaoQuinzena()}.
 * {@code DIA_DO_MES} exige {@link FiltrosAgente#getDiaDoMes()}.
 * {@code INICIO_MES}, {@code MEIO_MES}, {@code FIM_MES}, {@code VIRADA_MES}
 * e {@code DIA_DO_MES} podem usar {@link FiltrosAgente#getMesReferencia()}
 * (default ATUAL quando não mencionado).
 */
public enum TipoPeriodoAgente {
    /** "hoje", "vence hoje". */
    HOJE,
    /** "amanhã". */
    AMANHA,
    /** "essa semana", "na semana". Segunda a domingo da semana corrente. */
    SEMANA,
    /** "esse mês", "no mês". Do dia 1 ao último dia do mês corrente. */
    MES,
    /** "esse ano", "no ano". De 1º de janeiro a 31 de dezembro do ano corrente. */
    ANO,
    /** "vencidos", "atrasados", "em atraso". Tudo com vencimento < hoje. */
    VENCIDOS,
    /** "ontem". */
    ONTEM,
    /** "semana passada". Segunda a domingo da semana anterior à corrente. */
    SEMANA_PASSADA,
    /** "semana que vem", "próxima semana". Segunda a domingo da semana seguinte à corrente. */
    PROXIMA_SEMANA,
    /** "mês passado". Do dia 1 ao último dia do mês anterior ao corrente. */
    MES_PASSADO,
    /** "próximo mês", "mês que vem". Do dia 1 ao último dia do mês seguinte ao corrente. */
    PROXIMO_MES,

    /**
     * "até segunda", "até sexta-feira". Do dia de hoje até a próxima
     * ocorrência do dia da semana informado em
     * {@link FiltrosAgente#getDiaSemanaAlvo()} (inclusive). Se hoje já for
     * o próprio dia da semana pedido, o intervalo é só hoje.
     * <p>
     * ESTE É O VALOR QUE FALTAVA e causava o bug original: mensagens tipo
     * "o que tenho pra pagar até segunda" não tinham nenhum enum
     * correspondente, então a LLM mapeava (incorretamente) para o mais
     * parecido disponível ({@code PROXIMA_SEMANA}), gerando um intervalo
     * uma semana inteira maior do que o esperado.
     */
    ATE_DIA_SEMANA,

    /**
     * "quinzena", "primeira quinzena", "segunda quinzena do mês que vem".
     * Sem {@link FiltrosAgente#getPosicaoQuinzena()}: quinzena corrente com
     * base no dia de hoje (dias 1–15 ou 16–fim). Com posição: dias 1–15
     * (PRIMEIRA) ou 16–fim (SEGUNDA) do mês indicado por
     * {@link FiltrosAgente#getMesReferencia()} (default ATUAL).
     */
    QUINZENA,

    /** "fim de semana", "fds". Sábado e domingo mais próximos (desta semana). */
    FIM_DE_SEMANA,

    /** "fim de semana que vem", "próximo fim de semana". Sábado e domingo da semana seguinte. */
    PROXIMO_FIM_DE_SEMANA,

    /** "começo do mês", "início do mês". Dias 1–10 do mês em {@link FiltrosAgente#getMesReferencia()}. */
    INICIO_MES,

    /** "meio do mês". Dias 11–20 do mês em {@link FiltrosAgente#getMesReferencia()}. */
    MEIO_MES,

    /** "final do mês", "fim do mês". Dias 21–fim do mês em {@link FiltrosAgente#getMesReferencia()}. */
    FIM_MES,

    /**
     * "virada do mês", "de um mês pro outro", "o que passa de julho pra
     * agosto". Semana comercial (segunda a sexta) que atravessa a fronteira
     * entre dois meses: a última segunda-feira do mês de referência cuja
     * sexta-feira correspondente já cai no mês seguinte.
     */
    VIRADA_MES,

    /**
     * "até o dia 15", "o que vence dia 20". Do dia de hoje até o dia
     * numérico informado em {@link FiltrosAgente#getDiaDoMes()}, no mês
     * indicado por {@link FiltrosAgente#getMesReferencia()} (default ATUAL).
     */
    DIA_DO_MES,

    /** Intervalo explícito de datas informado pelo cliente ("de 01/07 a 15/07"). */
    INTERVALO,

    /**
     * Cliente não mencionou período, ou mencionou um recorte que não se
     * encaixa em nenhum valor acima (dia útil, feriado, evento como "antes
     * do Natal", período vago sem data). Não filtra por data.
     */
    SEM_FILTRO
}