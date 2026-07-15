package com.agenda.shared.exception;

/**
 * Exceção de assinatura insuficiente ou bloqueada (HTTP 402).
 *
 * Lançada quando a operação exige ajuste na assinatura da empresa:
 * criar loja além do contratado, ou escrever com assinatura
 * inadimplente/cancelada. Capturada pelo RestExceptionHandler
 * que retorna 402 com a mensagem.
 */
public class PagamentoRequeridoException extends RuntimeException {
    public PagamentoRequeridoException(String message) {
        super(message);
    }
}
