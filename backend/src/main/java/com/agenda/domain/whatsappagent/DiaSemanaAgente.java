package com.agenda.domain.whatsappagent;

/**
 * Dia da semana mencionado pelo cliente como limite de um período, usado
 * junto de {@link TipoPeriodoAgente#ATE_DIA_SEMANA} (ex.: "até segunda",
 * "até sexta-feira"). A LLM só identifica QUAL dia foi mencionado — o
 * cálculo de "qual é a próxima segunda/sexta a partir de hoje" é feito
 * em código, em {@link WhatsAppAgentService#resolverPeriodo}.
 */
public enum DiaSemanaAgente {
    SEGUNDA, TERCA, QUARTA, QUINTA, SEXTA, SABADO, DOMINGO
}