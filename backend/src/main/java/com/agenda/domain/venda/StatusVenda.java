package com.agenda.domain.venda;

/**
 * Status de uma venda do PDV.
 * <p>
 * Só dois, e de propósito: não existe editar venda. Corrigir é cancelar e
 * lançar de novo (§5.3 do plano) — venda editável é histórico que muda sozinho.
 * {@code CANCELADA} devolve o estoque e sai de toda agregação do relatório.
 */
public enum StatusVenda {
    CONCLUIDA,
    CANCELADA
}
