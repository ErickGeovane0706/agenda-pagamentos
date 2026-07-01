package com.agenda.domain.whatsappagent;

/**
 * Como o cliente delimitou o período da consulta. {@code INTERVALO} exige
 * que {@link FiltrosAgente#getDataInicio()} e {@link FiltrosAgente#getDataFim()}
 * estejam preenchidas; os demais valores são resolvidos em código (nunca
 * pela LLM) a partir da data corrente — ver
 * {@link WhatsAppAgentService#resolverPeriodo}.
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
    /** Intervalo explícito de datas informado pelo cliente ("de 01/07 a 15/07"). */
    INTERVALO,
    /** Cliente não mencionou período — não filtra por data. */
    SEM_FILTRO
}