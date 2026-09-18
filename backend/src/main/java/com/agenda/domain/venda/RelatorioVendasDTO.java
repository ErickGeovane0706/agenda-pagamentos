package com.agenda.domain.venda;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Relatório de vendas de um período, numa loja.
 *
 * @param margemPercentual <b>Nulo quando não houve venda no período</b>, e a
 *        tela mostra "—". Devolver 0 seria mentira: 0% de margem é um negócio
 *        que vendeu sem lucro, o que é bem diferente de um negócio que não
 *        vendeu (§8 do plano).
 * @param porProduto Ordenado pelo lucro, do maior para o menor — é o que
 *        responde "qual produto dá dinheiro", que é a pergunta da tela.
 *        Calculado a partir de {@code venda_itens}, <b>nunca</b> de
 *        {@code produtos}.
 */
public record RelatorioVendasDTO(
    BigDecimal receita,
    BigDecimal custo,
    BigDecimal lucro,
    BigDecimal margemPercentual,
    long quantidadeVendas,
    List<LinhaProduto> porProduto
) {
    /**
     * Uma linha da quebra por produto.
     * <p>
     * {@code produtoNome} vem da <b>cópia</b> gravada no item, não do cadastro:
     * o relatório continua legível mesmo para produto já desativado, e não
     * muda de nome se alguém renomear o produto depois.
     * <p>
     * A soma destas receitas pode divergir da {@link #receita} do cabeçalho em
     * centavos quando há quantidade fracionada, porque o total da venda é
     * arredondado por linha na gravação e aqui a soma é do produto bruto. O
     * número do cabeçalho é o que valeu na venda, e é o que manda.
     */
    public record LinhaProduto(
        UUID produtoId,
        String produtoNome,
        BigDecimal quantidade,
        BigDecimal receita,
        BigDecimal custo,
        BigDecimal lucro
    ) {}
}
