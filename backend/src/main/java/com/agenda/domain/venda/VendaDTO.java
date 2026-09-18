package com.agenda.domain.venda;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO de uma venda, com os itens. O {@code lucro} viaja calculado porque a tela
 * de venda mostra o resultado na hora — e calcular no servidor evita que a
 * conta exista em dois lugares com arredondamentos diferentes.
 */
public record VendaDTO(
    UUID id,
    UUID lojaId,
    String clienteNome,
    BigDecimal total,
    BigDecimal custoTotal,
    BigDecimal lucro,
    StatusVenda status,
    LocalDateTime vendidoEm,
    List<ItemDTO> itens
) {
    public record ItemDTO(
        UUID produtoId,
        String produtoNome,
        BigDecimal quantidade,
        BigDecimal precoCusto,
        BigDecimal precoVenda
    ) {}

    public static VendaDTO from(Venda v) {
        var itens = v.getItens().stream()
            .map(i -> new ItemDTO(i.getProdutoId(), i.getProdutoNome(),
                i.getQuantidade(), i.getPrecoCusto(), i.getPrecoVenda()))
            .toList();
        return new VendaDTO(v.getId(), v.getLoja().getId(), v.getClienteNome(),
            v.getTotal(), v.getCustoTotal(), v.getTotal().subtract(v.getCustoTotal()),
            v.getStatus(), v.getVendidoEm(), itens);
    }
}
