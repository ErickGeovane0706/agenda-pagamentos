package com.agenda.domain.pix;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PagamentoPixRepository extends JpaRepository<PagamentoPix, UUID>, JpaSpecificationExecutor<PagamentoPix> {

    List<PagamentoPix> findByEmpresa_Id(UUID empresaId);

    // método "listar" antigo REMOVIDO — agora via Specification no Service

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

    @Query("SELECT p FROM PagamentoPix p WHERE p.empresa.id = :empresaId AND p.status = 'PENDENTE' AND p.vencimento = :data")
    List<PagamentoPix> findPendentesVencendoEm(@Param("empresaId") UUID empresaId, @Param("data") LocalDate data);

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