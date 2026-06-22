package com.agenda.domain.pix;

/**
 * Ciclo de vida de um pagamento PIX.
 * <p>
 * Mesmos estados de {@link com.agenda.domain.boleto.StatusBoleto}:
 * PENDENTE → PAGO / VENCIDO / CANCELADO.
 * A transição para VENCIDO é automática (job noturno).
 * </p>
 */
public enum StatusPix {
    PENDENTE, PAGO, VENCIDO, CANCELADO
}
