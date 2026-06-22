package com.agenda.domain.pix;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repositório JPA para {@link PagamentoPix}.
 * <p>
 * Assim como {@link com.agenda.domain.boleto.BoletoRepository}, oferece
 * consultas agrupadas para resumo de dashboard, vencidos, pendentes por
 * data e exportação CSV — sempre filtradas por empresa (multi-tenant).
 * </p>
 */
public interface PagamentoPixRepository extends JpaRepository<PagamentoPix, UUID>, JpaSpecificationExecutor<PagamentoPix> {

    List<PagamentoPix> findByEmpresa_Id(UUID empresaId);

    /**
     * Agrega por status: total de registros + soma de valores no período.
     * Usado pelo dashboard para exibir cards de resumo de PIX.
     */
    @Query("""
        SELECT p.status, COUNT(p), SUM(p.valor) FROM PagamentoPix p
        WHERE p.empresa.id = :empresaId
        AND (:lojaId IS NULL OR p.loja.id = :lojaId)
        AND p.vencimento BETWEEN :de AND :ate
        GROUP BY p.status
    """)
    List<Object[]> resumo(
            @Param("empresaId") UUID empresaId,
            @Param("lojaId") UUID lojaId,
            @Param("de") LocalDate de,
            @Param("ate") LocalDate ate
    );

    /** PIX pendentes que vencem exatamente em {@code data} (job notificacao). */
    @Query("SELECT p FROM PagamentoPix p WHERE p.empresa.id = :empresaId AND p.status = 'PENDENTE' AND p.vencimento = :data")
    List<PagamentoPix> findPendentesVencendoEm(@Param("empresaId") UUID empresaId, @Param("data") LocalDate data);

    /** PIX pendentes com vencimento anterior a hoje — candidatos a VENCIDO. */
    @Query("SELECT p FROM PagamentoPix p WHERE p.empresa.id = :empresaId AND p.status = 'PENDENTE' AND p.vencimento < :hoje ORDER BY p.vencimento ASC")
    List<PagamentoPix> findVencidos(@Param("empresaId") UUID empresaId, @Param("hoje") LocalDate hoje);

    /** PIX pendentes com vencimento em um intervalo (exportação/job). */
    @Query("SELECT p FROM PagamentoPix p WHERE p.empresa.id = :empresaId AND p.status = 'PENDENTE' AND p.vencimento BETWEEN :de AND :ate ORDER BY p.vencimento ASC")
    List<PagamentoPix> findPendentesEntre(@Param("empresaId") UUID empresaId, @Param("de") LocalDate de, @Param("ate") LocalDate ate);

    /**
     * Projeção enxuta para geração de CSV (sem joins desnecessários).
     * Retorna apenas fornecedor, valor, vencimento e status.
     */
    @Query("""
        SELECT p.fornecedor, p.valor, p.vencimento, p.status FROM PagamentoPix p
        WHERE p.empresa.id = :empresaId
        AND (:lojaId IS NULL OR p.loja.id = :lojaId)
        AND p.vencimento BETWEEN :de AND :ate
        ORDER BY p.vencimento ASC
    """)
    List<Object[]> listarParaCsv(
            @Param("empresaId") UUID empresaId,
            @Param("lojaId") UUID lojaId,
            @Param("de") LocalDate de,
            @Param("ate") LocalDate ate
    );
}