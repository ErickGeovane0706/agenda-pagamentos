package com.agenda.domain.pix;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO de entrada para editar um pagamento PIX existente.
 * <p>
 * Os mesmos campos obrigatórios da criação — a edição substitui todos os
 * valores (PUT lógico via {@code @PutMapping} no controller).
 * </p>
 */
public record EditarPixRequest(
    @NotNull UUID lojaId,
    @NotBlank @Size(max = 300) String fornecedor,
    @NotNull @DecimalMin("0.01") BigDecimal valor,
    @NotNull LocalDate vencimento,
    @NotBlank String chavePix,
    String tipoChave,
    String observacoes
) {}
