package com.agenda.domain.cheque;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO de entrada para criar um Cheque.
 * <p>
 * Diferencia-se dos DTOs de boleto/PIX por ter {@code bancoId} (opcional —
 * referência à tabela de bancos cadastrados) e {@code numeroCheque}
 * (opcional — número do cheque físico para controle).
 * </p>
 */
public record CriarChequeRequest(
    @NotNull UUID lojaId,
    @NotBlank @Size(max = 300) String fornecedor,
    @NotNull @DecimalMin("0.01") BigDecimal valor,
    @NotNull LocalDate vencimento,
    UUID bancoId,
    String numeroCheque,
    String observacoes
) {}
