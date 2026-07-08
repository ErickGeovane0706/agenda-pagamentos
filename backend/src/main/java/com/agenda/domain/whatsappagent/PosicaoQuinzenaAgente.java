package com.agenda.domain.whatsappagent;

/**
 * Qual metade do mês o cliente quis dizer com "quinzena", usado junto de
 * {@link TipoPeriodoAgente#QUINZENA}. Quando o cliente não especifica
 * ("o que vence na quinzena?"), este campo fica {@code null} e o código
 * resolve a quinzena corrente com base no dia de hoje.
 */
public enum PosicaoQuinzenaAgente {
    /** Dias 1 a 15 do mês de referência. */
    PRIMEIRA,
    /** Dia 16 ao último dia do mês de referência. */
    SEGUNDA
}