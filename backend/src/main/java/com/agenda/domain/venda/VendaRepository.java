package com.agenda.domain.venda;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repositório JPA para {@link Venda}.
 */
public interface VendaRepository extends JpaRepository<Venda, UUID> {

    Page<Venda> findByEmpresaIdAndLojaIdOrderByVendidoEmDesc(UUID empresaId, UUID lojaId, Pageable pageable);

    /**
     * Cancela a venda <b>se ela ainda estiver concluída</b>, e devolve quantas
     * linhas mudaram.
     * <p>
     * A condição no {@code WHERE} é o que torna o cancelamento seguro: sem ela,
     * dois cliques no botão devolveriam o estoque duas vezes e criariam
     * mercadoria do nada (§5.3 do plano). Quem decide é o banco, num comando
     * atômico — não um {@code if} em Java entre a leitura e a gravação.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Venda v
           SET v.status = com.agenda.domain.venda.StatusVenda.CANCELADA
         WHERE v.id = :id
           AND v.status = com.agenda.domain.venda.StatusVenda.CONCLUIDA
        """)
    int cancelarSeConcluida(@Param("id") UUID id);

    /**
     * Totais do período: receita, custo e quantidade de vendas.
     * <p>
     * <b>A soma é do Postgres, não de Java.</b> Trazer as vendas do período
     * para somar na aplicação funciona com 50 vendas e morre com 50 mil, e o
     * volume de uma loja em operação cresce mais rápido do que a intuição
     * sugere.
     * <p>
     * O {@code COALESCE} existe porque {@code SUM} de conjunto vazio é
     * {@code NULL}, não zero: sem ele, um período sem venda viraria
     * {@code NullPointerException} em vez de relatório zerado.
     * <p>
     * As colunas do {@code WHERE} seguem a ordem de {@code idx_vendas_relatorio}
     * (empresa, loja, status, data).
     */
    @Query("""
        SELECT COALESCE(SUM(v.total), 0), COALESCE(SUM(v.custoTotal), 0), COUNT(v)
          FROM Venda v
         WHERE v.empresa.id = :empresaId
           AND v.loja.id = :lojaId
           AND v.status = com.agenda.domain.venda.StatusVenda.CONCLUIDA
           AND v.vendidoEm >= :de
           AND v.vendidoEm < :ate
        """)
    // Devolve List<Object[]> e não Object[]: para uma projeção de tuplas o
    // Spring Data entrega uma LISTA de linhas, cada linha um Object[]. Declarar
    // Object[] aqui faz chegar um array contendo um array, e o cast estoura em
    // ClassCastException. Como há GROUP BY nenhum, a lista tem sempre uma linha.
    List<Object[]> totaisDoPeriodo(@Param("empresaId") UUID empresaId,
                                   @Param("lojaId") UUID lojaId,
                                   @Param("de") LocalDateTime de,
                                   @Param("ate") LocalDateTime ate);

    /**
     * Quebra por produto, a partir de {@code venda_itens}.
     * <p>
     * ⚠️ <b>Repare que não há {@code JOIN} com {@code Produto} aqui, e não pode
     * haver.</b> Preço e nome saem do próprio item, que guarda a cópia do
     * instante da venda. Um {@code JOIN produtos} para "pegar o preço" faria o
     * lucro de junho mudar em agosto a cada reajuste de cadastro — sem erro,
     * sem log, sem nada. É o §5.1 do plano, e o
     * {@code RelatorioVendasIntegrationTest} existe para pegar exatamente isso.
     * <p>
     * A ordenação é por lucro decrescente: a pergunta da tela é "qual produto
     * dá dinheiro".
     */
    @Query("""
        SELECT i.produtoId,
               i.produtoNome,
               SUM(i.quantidade),
               SUM(i.quantidade * i.precoVenda),
               SUM(i.quantidade * i.precoCusto)
          FROM VendaItem i
         WHERE i.venda.empresa.id = :empresaId
           AND i.venda.loja.id = :lojaId
           AND i.venda.status = com.agenda.domain.venda.StatusVenda.CONCLUIDA
           AND i.venda.vendidoEm >= :de
           AND i.venda.vendidoEm < :ate
         GROUP BY i.produtoId, i.produtoNome
         ORDER BY SUM(i.quantidade * i.precoVenda) - SUM(i.quantidade * i.precoCusto) DESC
        """)
    List<Object[]> quebraPorProduto(@Param("empresaId") UUID empresaId,
                                    @Param("lojaId") UUID lojaId,
                                    @Param("de") LocalDateTime de,
                                    @Param("ate") LocalDateTime ate);
}
