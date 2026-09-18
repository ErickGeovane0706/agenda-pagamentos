package com.agenda.domain.venda;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DTO de entrada para registrar uma venda.
 * <p>
 * Vários itens por venda (decidido em 29/08). {@code clienteNome} é opcional —
 * quem passa no caixa e não quer se identificar não deve travar a venda.
 */
public record CriarVendaRequest(
    @NotNull UUID lojaId,
    @Size(max = 300) String clienteNome,
    @NotEmpty @Valid List<Item> itens
) {
    /**
     * {@code precoVenda} é <b>opcional</b>: nulo significa "use o preço do
     * cadastro". Preço editável na venda foi decidido em 27/08, porque o valor
     * praticado no balcão nem sempre é o da tabela.
     * <p>
     * Repare que <b>não existe {@code precoCusto} aqui</b>, e isso é
     * intencional: custo vem sempre do produto, nunca do cliente HTTP. Aceitar
     * custo de fora deixaria a margem do relatório à mercê de quem chama a API.
     */
    public record Item(
        @NotNull UUID produtoId,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantidade,
        @DecimalMin("0") BigDecimal precoVenda
    ) {}
}
