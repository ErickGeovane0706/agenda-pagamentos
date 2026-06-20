package com.agenda.domain.cheque;

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

public interface ChequeRepository extends JpaRepository<Cheque, UUID>, JpaSpecificationExecutor<Cheque> {

    List<Cheque> findByEmpresa_Id(UUID empresaId);

    // método "listar" antigo REMOVIDO — agora via Specification no Service

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

    @Query("SELECT c FROM Cheque c WHERE c.empresa.id = :empresaId AND c.status = 'PENDENTE' AND c.vencimento = :data")
    List<Cheque> findPendentesVencendoEm(@Param("empresaId") UUID empresaId, @Param("data") LocalDate data);

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