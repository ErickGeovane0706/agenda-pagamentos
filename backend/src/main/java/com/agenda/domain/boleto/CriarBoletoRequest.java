package com.agenda.domain.boleto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO de entrada para criar um novo boleto.
 * <p>
 * {@code lojaId}, {@code fornecedor}, {@code valor} e {@code vencimento} são obrigatórios.
 * {@code codigoBarras} e {@code observacoes} são opcionais.
 * {@code confirmarDuplicado = true} é a resposta do usuário ao aviso de boleto
 * já cadastrado (409): grava mesmo assim.
 * </p>
 */
public record CriarBoletoRequest(
    @NotNull UUID lojaId,
    @NotBlank @Size(max = 300) String fornecedor,
    @NotNull @DecimalMin("0.01") BigDecimal valor,
    @NotNull LocalDate vencimento,
    String codigoBarras,
    String observacoes,
    Boolean confirmarDuplicado
) {}
