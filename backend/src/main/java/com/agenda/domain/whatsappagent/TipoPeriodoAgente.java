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
    /** "vencidos", "atrasados", "em atraso". Tudo com vencimento < hoje. */
    VENCIDOS,
    /** Intervalo explícito de datas informado pelo cliente ("de 01/07 a 15/07"). */
    INTERVALO,
    /** Cliente não mencionou período — não filtra por data. */
    SEM_FILTRO
}
