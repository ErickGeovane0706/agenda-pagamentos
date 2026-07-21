package com.agenda.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler global de exceções (@RestControllerAdvice).
 * Mapeia exceções conhecidas para respostas HTTP padronizadas
 * (ErrorResponse). Inclui tratamento customizado para erros
 * de validação (Bean Validation), autenticação (Spring Security)
 * e exceções de negócio (NotFoundException, AccessDeniedException).
 */
@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    /**
     * Recurso não encontrado → 404 NOT_FOUND.
     * Ex.: boleto com ID inexistente.
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Acesso negado por regra de negócio → 403 FORBIDDEN.
     * Ex.: usuário tenta acessar dados de outra empresa (violação de tenant).
     */
    @ExceptionHandler(com.agenda.shared.exception.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(com.agenda.shared.exception.AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /**
     * Assinatura insuficiente ou bloqueada → 402 PAYMENT_REQUIRED.
     * Ex.: criar loja além do contratado na assinatura.
     */
    @ExceptionHandler(PagamentoRequeridoException.class)
    public ResponseEntity<ErrorResponse> handlePagamentoRequerido(PagamentoRequeridoException ex) {
        return error(HttpStatus.PAYMENT_REQUIRED, ex.getMessage());
    }

    /**
     * Argumento inválido → 400 BAD_REQUEST.
     * Ex.: UUID mal formatado, parâmetro obrigatório ausente.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Falha na comunicação com o gateway de pagamento → 502 BAD_GATEWAY.
     * A mensagem do AsaasException já vem sanitizada (sem api-key nem PII).
     */
    @ExceptionHandler(com.agenda.domain.assinatura.AsaasException.class)
    public ResponseEntity<ErrorResponse> handleAsaas(com.agenda.domain.assinatura.AsaasException ex) {
        log.warn("Falha no gateway de pagamento: {}", ex.getMessage());
        return error(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    /**
     * Erro de validação Bean Validation → 422 UNPROCESSABLE_ENTITY.
     * Agrega todos os erros de campo em uma única string separada por ";".
     */
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

    /**
     * Acesso negado pelo Spring Security → 403 FORBIDDEN.
     * Ex.: endpoint protegido sem token válido.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleSpringAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Acesso negado");
    }

    /**
     * Falha de autenticação (Spring Security) → 401 UNAUTHORIZED.
     * Ex.: credenciais inválidas no login.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthenticationException ex) {
        log.warn("Falha de autenticação: {}", ex.getMessage());
        return error(HttpStatus.UNAUTHORIZED, "Não autenticado");
    }

    /**
     * Excesso de tentativas → 429 TOO_MANY_REQUESTS.
     * Ex.: tentativas de login demais para o mesmo email.
     */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex) {
        return error(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    /**
     * Método HTTP não suportado pela rota → 405 METHOD_NOT_ALLOWED.
     * Ex.: GET em /api/auth/login (que só aceita POST).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "Método não permitido");
    }

    /**
     * Corpo da requisição ilegível → 400 BAD_REQUEST.
     * Ex.: JSON malformado ou corpo ausente onde é obrigatório.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "Requisição malformada");
    }

    /**
     * Content-Type não suportado → 415 UNSUPPORTED_MEDIA_TYPE.
     * Ex.: POST com text/plain em endpoint que espera application/json.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato não suportado");
    }

    /**
     * Tipo de parâmetro incompatível → 400 BAD_REQUEST.
     * Ex.: UUID inválido no path ou query string.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "Parâmetro inválido");
    }

    /**
     * Parâmetro obrigatório ausente → 400 BAD_REQUEST.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return error(HttpStatus.BAD_REQUEST, "Parâmetro obrigatório ausente");
    }

    /**
     * Rota inexistente → 404 NOT_FOUND.
     * Alcançável nos prefixos permitAll (ex.: /webhook/*), onde o Spring
     * Security não barra antes do dispatcher.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "Recurso não encontrado");
    }

    /**
     * Qualquer exceção não tratada → 500 INTERNAL_SERVER_ERROR.
     * Loga o stack trace completo para debug e retorna mensagem genérica.
     */
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
