package com.agenda.domain.cheque;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO de entrada para editar um Cheque existente.
 * <p>
 * Todos os campos são substituídos (PUT lógico). Se {@code bancoId} for
 * {@code null}, a referência ao banco é removida.
 * </p>
 */
public record EditarChequeRequest(
    @NotNull UUID lojaId,
    @NotBlank @Size(max = 300) String fornecedor,
    @NotNull @DecimalMin("0.01") BigDecimal valor,
    @NotNull LocalDate vencimento,
    UUID bancoId,
    String numeroCheque,
    String observacoes
) {}
