package com.agenda.domain.boleto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO de entrada para editar um boleto existente.
 * <p>
 * Os mesmos campos obrigatórios de criação são exigidos aqui também,
 * pois a edição substitui todos os valores (PUT lógico via PATCH
 * no controller, mas o service usa o DTO completo).
 * </p>
 */
public record EditarBoletoRequest(
    @NotNull UUID lojaId,
    @NotBlank @Size(max = 300) String fornecedor,
    @NotNull @DecimalMin("0.01") BigDecimal valor,
    @NotNull LocalDate vencimento,
    String codigoBarras,
    String observacoes
) {}
