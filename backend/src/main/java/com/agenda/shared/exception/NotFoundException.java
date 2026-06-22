package com.agenda.shared.exception;

/**
 * Exceção de recurso não encontrado (HTTP 404).
 *
 * Lançada quando uma entidade solicitada não existe no banco de dados.
 * Capturada pelo RestExceptionHandler que retorna 404 com a mensagem.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
