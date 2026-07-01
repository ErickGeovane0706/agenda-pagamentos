package com.agenda.domain.whatsappagent;

/**
 * Substitui o antigo {@code incluirPagos: boolean} de {@link FiltrosAgente}.
 * O boolean só permitia dois modos ("só pendente" / "pendente + pago
 * misturados"), mas não existia forma de o cliente pedir "só o que já foi
 * pago". Ver categoria G da suite de teste do classificador.
 */
public enum FiltroStatusAgente {
    /** Padrão: só o que ainda não foi pago. "quanto tenho pra pagar", "o que vence hoje". */
    PENDENTE,
    /** Só o que já foi pago/compensado. "o que já foi pago essa semana". */
    PAGO,
    /** Pago e pendente juntos (exclui só CANCELADO). "resumo com pago e pendente". */
    TODOS
}