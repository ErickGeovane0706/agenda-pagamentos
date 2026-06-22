package com.agenda.domain.cheque;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repositório JPA para {@link Cheque}.
 * <p>
 * Mesma estrutura de {@link com.agenda.domain.boleto.BoletoRepository} e
 * {@link com.agenda.domain.pix.PagamentoPixRepository}: queries nomeadas
 * para resumo de dashboard, pendentes por data, vencidos e exportação CSV.
 * </p>
 */
public interface ChequeRepository extends JpaRepository<Cheque, UUID>, JpaSpecificationExecutor<Cheque> {

    List<Cheque> findByEmpresa_Id(UUID empresaId);

    /**
     * Agrega por status: contagem + soma de valores no período.
     * Usado pelo dashboard para cards de resumo.
     */
    @Query("""
        SELECT c.status, COUNT(c), SUM(c.valor) FROM Cheque c
        WHERE c.empresa.id = :empresaId
        AND (:lojaId IS NULL OR c.loja.id = :lojaId)
        AND c.vencimento BETWEEN :de AND :ate
        GROUP BY c.status
    """)
    List<Object[]> resumo(
            @Param("empresaId") UUID empresaId,
            @Param("lojaId") UUID lojaId,
            @Param("de") LocalDate de,
            @Param("ate") LocalDate ate
    );

    /** Cheques pendentes que vencem exatamente em {@code data} (job notificacao). */
    @Query("SELECT c FROM Cheque c WHERE c.empresa.id = :empresaId AND c.status = 'PENDENTE' AND c.vencimento = :data")
    List<Cheque> findPendentesVencendoEm(@Param("empresaId") UUID empresaId, @Param("data") LocalDate data);

    /** Cheques pendentes vencidos — candidatos a notificação de atraso (não viram DEVOLVIDO automaticamente). */
    @Query("SELECT c FROM Cheque c WHERE c.empresa.id = :empresaId AND c.status = 'PENDENTE' AND c.vencimento < :hoje ORDER BY c.vencimento ASC")
    List<Cheque> findVencidos(@Param("empresaId") UUID empresaId, @Param("hoje") LocalDate hoje);

    /** Cheques pendentes com vencimento em um intervalo (exportação/job). */
    @Query("SELECT c FROM Cheque c WHERE c.empresa.id = :empresaId AND c.status = 'PENDENTE' AND c.vencimento BETWEEN :de AND :ate ORDER BY c.vencimento ASC")
    List<Cheque> findPendentesEntre(@Param("empresaId") UUID empresaId, @Param("de") LocalDate de, @Param("ate") LocalDate ate);

    /**
     * Projeção enxuta para CSV (sem joins em loja/banco).
     */
    @Query("""
        SELECT c.fornecedor, c.valor, c.vencimento, c.status FROM Cheque c
        WHERE c.empresa.id = :empresaId
        AND (:lojaId IS NULL OR c.loja.id = :lojaId)
        AND c.vencimento BETWEEN :de AND :ate
        ORDER BY c.vencimento ASC
    """)
    List<Object[]> listarParaCsv(
            @Param("empresaId") UUID empresaId,
            @Param("lojaId") UUID lojaId,
            @Param("de") LocalDate de,
            @Param("ate") LocalDate ate
    );
}