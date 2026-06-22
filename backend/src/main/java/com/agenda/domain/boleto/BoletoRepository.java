package com.agenda.domain.boleto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repositório JPA para {@link Boleto}.
 * <p>
 * Consultas paginadas/dinâmicas usam {@link BoletoSpecification} via
 * {@code JpaSpecificationExecutor}. Os métodos nativos {@code @Query}
 * atendem relatórios e jobs agendados (notificações, CSV).
 * </p>
 */
public interface BoletoRepository extends JpaRepository<Boleto, UUID>, JpaSpecificationExecutor<Boleto> {

    List<Boleto> findByEmpresa_Id(UUID empresaId);

    /**
     * Agrupa boletos por status no período — usado pelo dashboard
     * (quantidade e soma de valor por PENDENTE/PAGO/VENCIDO/CANCELADO).
     */
    @Query("""
        SELECT b.status, COUNT(b), SUM(b.valor) FROM Boleto b
        WHERE b.empresa.id = :empresaId
        AND (:lojaId IS NULL OR b.loja.id = :lojaId)
        AND b.vencimento BETWEEN :de AND :ate
        GROUP BY b.status
    """)
    List<Object[]> resumo(
            @Param("empresaId") UUID empresaId,
            @Param("lojaId") UUID lojaId,
            @Param("de") LocalDate de,
            @Param("ate") LocalDate ate
    );

    /** Boletos PENDENTE com vencimento exato {@code data} → job de notificação. */
    @Query("SELECT b FROM Boleto b WHERE b.empresa.id = :empresaId AND b.status = 'PENDENTE' AND b.vencimento = :data")
    List<Boleto> findPendentesVencendoEm(@Param("empresaId") UUID empresaId, @Param("data") LocalDate data);

    /** Boletos PENDENTE com vencimento anterior a {@code hoje} → job de atualização para VENCIDO. */
    @Query("SELECT b FROM Boleto b WHERE b.empresa.id = :empresaId AND b.status = 'PENDENTE' AND b.vencimento < :hoje ORDER BY b.vencimento ASC")
    List<Boleto> findVencidos(@Param("empresaId") UUID empresaId, @Param("hoje") LocalDate hoje);

    /** Boletos PENDENTE em um intervalo → job de notificação de vencimentos futuros. */
    @Query("SELECT b FROM Boleto b WHERE b.empresa.id = :empresaId AND b.status = 'PENDENTE' AND b.vencimento BETWEEN :de AND :ate ORDER BY b.vencimento ASC")
    List<Boleto> findPendentesEntre(@Param("empresaId") UUID empresaId, @Param("de") LocalDate de, @Param("ate") LocalDate ate);

    /** Projeção enxuta para exportação CSV (fornecedor, valor, vencimento, status). */
    @Query("""
        SELECT b.fornecedor, b.valor, b.vencimento, b.status FROM Boleto b
        WHERE b.empresa.id = :empresaId
        AND (:lojaId IS NULL OR b.loja.id = :lojaId)
        AND b.vencimento BETWEEN :de AND :ate
        ORDER BY b.vencimento ASC
    """)
    List<Object[]> listarParaCsv(
            @Param("empresaId") UUID empresaId,
            @Param("lojaId") UUID lojaId,
            @Param("de") LocalDate de,
            @Param("ate") LocalDate ate
    );
}