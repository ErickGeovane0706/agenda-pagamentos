package com.agenda.domain.produto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO de Produto. Exclui o relacionamento com Empresa e o {@code criadoEm},
 * como {@link com.agenda.domain.loja.LojaDTO} faz.
 * <p>
 * {@code lojaId} viaja porque a tela de produtos é por loja e o front precisa
 * conferir de qual.
 */
public record ProdutoDTO(
    UUID id,
    UUID lojaId,
    String nome,
    BigDecimal quantidade,
    BigDecimal precoCusto,
    BigDecimal precoVenda,
    boolean ativo
) {
    public static ProdutoDTO from(Produto p) {
        return new ProdutoDTO(p.getId(), p.getLoja().getId(), p.getNome(),
            p.getQuantidade(), p.getPrecoCusto(), p.getPrecoVenda(), p.getAtivo());
    }
}
