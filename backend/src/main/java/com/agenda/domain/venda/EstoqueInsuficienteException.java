package com.agenda.domain.venda;

/**
 * Não havia estoque suficiente no instante da venda → <b>409 CONFLICT</b>.
 * <p>
 * 409 e não 400: o front precisa distinguir "seu formulário está errado" de
 * "não tem gelo suficiente agora". São mensagens e ações diferentes para quem
 * está no caixa — a primeira se corrige digitando, a segunda não.
 * <p>
 * A casa já tem precedente de exceção de domínio com status próprio
 * ({@code PagamentoRequeridoException} → 402).
 */
public class EstoqueInsuficienteException extends RuntimeException {
    public EstoqueInsuficienteException(String mensagem) {
        super(mensagem);
    }
}
