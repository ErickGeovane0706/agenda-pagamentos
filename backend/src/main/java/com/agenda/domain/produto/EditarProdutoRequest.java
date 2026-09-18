package com.agenda.domain.produto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * DTO de entrada para editar um Produto. Sem {@code lojaId}: produto não muda de
 * loja — transferência entre lojas está fora do escopo (§10 do plano).
 * <p>
 * {@code quantidade} é editável de propósito: é o caminho de ajuste quando o
 * estoque real diverge do sistema (§11), e o service audita a mudança.
 */
public record EditarProdutoRequest(
    @NotBlank @Size(max = 300) String nome,
    @NotNull @DecimalMin("0") BigDecimal quantidade,
    @NotNull @DecimalMin("0") BigDecimal precoCusto,
    @NotNull @DecimalMin("0") BigDecimal precoVenda
) {}
