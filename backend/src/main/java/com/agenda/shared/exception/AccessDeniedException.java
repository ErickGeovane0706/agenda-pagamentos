package com.agenda.shared.exception;

/**
 * Exceção de acesso negado (HTTP 403) - versão do domínio.
 *
 * Difere do AccessDeniedException do Spring Security por ser
 * lançada intencionalmente nas camadas de serviço quando uma
 * regra de negócio impede o usuário de executar a operação
 * (ex.: usuário tenta acessar dados de outra empresa).
 *
 * O RestExceptionHandler a captura e retorna 403 com a mensagem.
 */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
