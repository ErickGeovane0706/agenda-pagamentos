package com.agenda.domain.pix;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CriarPixRequest(
    @NotNull UUID lojaId,
    @NotBlank @Size(max = 300) String fornecedor,
    @NotNull @DecimalMin("0.01") BigDecimal valor,
    @NotNull LocalDate vencimento,
    @NotBlank String chavePix,
    String tipoChave,
    String observacoes
) {}
