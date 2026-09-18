package com.agenda.domain.produto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO de entrada para criar um Produto.
 * <p>
 * {@code DecimalMin("0")} e não {@code "0.01"} como em boleto/cheque: produto
 * pode nascer com estoque zero (cadastra hoje, recebe a carga amanhã), e preço
 * zero é legítimo para brinde. O que o banco recusa é <b>negativo</b>
 * ({@code ck_produto_precos_nao_negativos}), e a validação diz o mesmo.
 */
public record CriarProdutoRequest(
    @NotNull UUID lojaId,
    @NotBlank @Size(max = 300) String nome,
    @NotNull @DecimalMin("0") BigDecimal quantidade,
    @NotNull @DecimalMin("0") BigDecimal precoCusto,
    @NotNull @DecimalMin("0") BigDecimal precoVenda
) {}
