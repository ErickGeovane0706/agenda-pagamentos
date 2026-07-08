package com.agenda.domain.whatsappagent;

/**
 * A qual mês o cliente se refere quando o período depende de "mês de
 * referência" (ex.: "início do mês que vem", "quinzena do mês passado",
 * "até o dia 15" sem especificar mês). Usado junto de
 * {@link TipoPeriodoAgente#INICIO_MES}, {@link TipoPeriodoAgente#MEIO_MES},
 * {@link TipoPeriodoAgente#FIM_MES}, {@link TipoPeriodoAgente#VIRADA_MES},
 * {@link TipoPeriodoAgente#DIA_DO_MES} e {@link TipoPeriodoAgente#QUINZENA}
 * (quando {@link FiltrosAgente#getPosicaoQuinzena()} está preenchida).
 * <p>
 * Quando a LLM não preenche este campo (null) mas o período exige um mês
 * de referência, o código assume {@code ATUAL} como padrão — nunca deixa
 * o cálculo em aberto.
 */
public enum MesReferenciaAgente {
    ATUAL, PASSADO, PROXIMO
}