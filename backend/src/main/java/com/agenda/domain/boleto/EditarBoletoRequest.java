package com.agenda.domain.boleto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EditarBoletoRequest(
    @NotNull UUID lojaId,
    @NotBlank @Size(max = 300) String fornecedor,
    @NotNull @DecimalMin("0.01") BigDecimal valor,
    @NotNull LocalDate vencimento,
    String codigoBarras,
    String observacoes
) {}
