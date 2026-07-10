package com.agenda.shared.exception;

/**
 * Excesso de requisições / tentativas (HTTP 429).
 *
 * Lançada quando um teto de rate limit por identidade é atingido
 * (ex.: tentativas de login demais para o mesmo email). Capturada pelo
 * RestExceptionHandler que retorna 429 com a mensagem.
 */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
