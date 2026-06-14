package com.agenda.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(com.agenda.shared.exception.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(com.agenda.shared.exception.AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        var detalhe = ex.getBindingResult().getAllErrors().stream()
            .map(err -> {
                var field = err instanceof FieldError f ? f.getField() : err.getObjectName();
                return field + ": " + err.getDefaultMessage();
            })
            .collect(Collectors.joining("; "));
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "Erro de validação", detalhe);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleSpringAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Acesso negado");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthenticationException ex) {
        log.warn("Falha de autenticação: {}", ex.getMessage());
        return error(HttpStatus.UNAUTHORIZED, "Não autenticado");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Exceção não tratada: {}", ex.getMessage(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String mensagem) {
        return error(status, mensagem, null);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String mensagem, String detalhe) {
        var body = new ErrorResponse(status.value(), mensagem, detalhe, LocalDateTime.now());
        return ResponseEntity.status(status).body(body);
    }
}
