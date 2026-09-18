package com.agenda.domain.produto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Repositório JPA para {@link Produto}.
 * <p>
 * Sem {@code JpaSpecificationExecutor}: boleto e cheque precisam de filtro
 * dinâmico porque a agenda tem busca por status, faixa de data e fornecedor. A
 * lista de produtos de uma loja é uma consulta só.
 */
public interface ProdutoRepository extends JpaRepository<Produto, UUID> {

    List<Produto> findByEmpresaIdAndLojaIdAndAtivoTrueOrderByNome(UUID empresaId, UUID lojaId);

    /**
     * Existe outro produto <b>ativo</b> com este nome na loja?
     * <p>
     * O {@code AtivoTrue} não é detalhe: espelha o índice parcial
     * {@code uq_produto_nome_ativo ... WHERE ativo} da V25. Sem ele, desativar
     * "Ovo branco" e cadastrar outro com o mesmo nome seria recusado pelo
     * service mesmo com o banco permitindo — e o usuário levaria um "nome já
     * existe" apontando para um produto que ele não vê em tela nenhuma.
     */
    boolean existsByLojaIdAndNomeIgnoreCaseAndAtivoTrue(UUID lojaId, String nome);

    /** Mesma checagem, ignorando o próprio produto — para a edição não colidir consigo. */
    boolean existsByLojaIdAndNomeIgnoreCaseAndAtivoTrueAndIdNot(UUID lojaId, String nome, UUID id);

    /**
     * Baixa {@code qtd} do estoque <b>se houver estoque</b>, e devolve quantas
     * linhas mudaram — 0 significa que não havia.
     * <p>
     * A condição {@code quantidade >= :qtd} dentro do próprio {@code UPDATE} é
     * o ponto todo. Ler a quantidade, conferir num {@code if} em Java e só
     * então gravar é corrida clássica: dois caixas vendendo ao mesmo tempo
     * passam os dois pelo {@code if} e vendem o que não existe. Aqui quem
     * decide é o banco, num comando atômico.
     * <p>
     * Mesmo padrão de {@code AssinaturaRepository.vincularSubscriptionSeAusente}
     * — é a casa, não invenção.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Produto p
           SET p.quantidade = p.quantidade - :qtd,
               p.atualizadoEm = CURRENT_TIMESTAMP
         WHERE p.id = :id
           AND p.quantidade >= :qtd
        """)
    int baixarEstoqueSeHouver(@Param("id") UUID id, @Param("qtd") BigDecimal qtd);

    /**
     * Devolve {@code qtd} ao estoque, no cancelamento da venda.
     * <p>
     * Sem condição de quantidade: devolver sempre pode. A proteção contra
     * devolver duas vezes está um nível acima, no {@code UPDATE} condicional do
     * status da venda — é ele que garante que este comando roda uma vez só.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Produto p
           SET p.quantidade = p.quantidade + :qtd,
               p.atualizadoEm = CURRENT_TIMESTAMP
         WHERE p.id = :id
        """)
    int devolverEstoque(@Param("id") UUID id, @Param("qtd") BigDecimal qtd);
}
