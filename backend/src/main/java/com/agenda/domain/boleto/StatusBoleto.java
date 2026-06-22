package com.agenda.domain.boleto;

/**
 * Ciclo de vida de um boleto.
 * <p>
 * A transição esperada é PENDENTE → PAGO (pagamento manual) ou PENDENTE → VENCIDO
 * (automático via job noturno). CANCELADO pode ser acionado a qualquer momento
 * pelo usuário com permissão ADMIN/OPERADOR.
 * </p>
 */
public enum StatusBoleto {
    PENDENTE, PAGO, VENCIDO, CANCELADO
}
