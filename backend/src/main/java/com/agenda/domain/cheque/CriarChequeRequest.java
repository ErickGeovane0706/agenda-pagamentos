package com.agenda.domain.cheque;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CriarChequeRequest(
    @NotNull UUID lojaId,
    @NotBlank @Size(max = 300) String fornecedor,
    @NotNull @DecimalMin("0.01") BigDecimal valor,
    @NotNull LocalDate vencimento,
    UUID bancoId,
    String numeroCheque,
    String observacoes
) {}
