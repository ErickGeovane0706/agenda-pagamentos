package com.agenda.domain.cheque;

/**
 * Ciclo de vida de um Cheque.
 * <p>
 * Difere de {@link com.agenda.domain.boleto.StatusBoleto} e
 * {@link com.agenda.domain.pix.StatusPix} por possuir {@code DEVOLVIDO}
 * (cheque sem fundos/devolvido pelo banco) em vez de {@code VENCIDO}.
 * </p>
 * <ul>
 *   <li>PENDENTE → COMPENSADO / DEVOLVIDO / CANCELADO</li>
 *   <li>COMPENSADO: o cheque foi processado e o valor creditado.</li>
 *   <li>DEVOLVIDO: o cheque foi devolvido (C6 — sem fundos, C11 — etc.).</li>
 * </ul>
 */
public enum StatusCheque {
    PENDENTE, COMPENSADO, DEVOLVIDO, CANCELADO
}
