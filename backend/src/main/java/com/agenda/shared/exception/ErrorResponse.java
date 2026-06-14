package com.agenda.shared.exception;

import java.time.LocalDateTime;

public record ErrorResponse(
    int status,
    String mensagem,
    String detalhe,
    LocalDateTime timestamp
) {}
