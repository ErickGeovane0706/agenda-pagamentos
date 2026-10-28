package com.agenda.domain.pix;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO de entrada do "PIX recorrente": os mesmos dados de {@link CriarPixRequest},
 * mas com várias datas — cria um PIX por data (ex.: aluguel de vários meses).
 * <p>
 * Limite de 36 datas para um erro de digitação não gerar centenas de PIX.
 * </p>
 */
public record CriarPixRecorrenteRequest(
    @NotNull UUID lojaId,
    @NotBlank @Size(max = 300) String fornecedor,
    @NotNull @DecimalMin("0.01") BigDecimal valor,
    @NotEmpty @Size(max = 36) List<@NotNull LocalDate> vencimentos,
    @NotBlank String chavePix,
    String tipoChave,
    String observacoes
) {}
