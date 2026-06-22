package com.agenda.shared.exception;

import java.time.LocalDateTime;

/**
 * Padrão de resposta para erros da API.
 *
 * Retorna status HTTP, mensagem amigável, detalhe técnico
 * (ex.: erros de validação por campo) e timestamp da ocorrência.
 * Usado pelo RestExceptionHandler em todas as respostas de erro.
 */
public record ErrorResponse(
    int status,
    String mensagem,
    String detalhe,
    LocalDateTime timestamp
) {}
