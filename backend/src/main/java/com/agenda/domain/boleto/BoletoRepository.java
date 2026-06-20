package com.agenda.domain.boleto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BoletoRepository extends JpaRepository<Boleto, UUID> {
    List<Boleto> findByEmpresa_Id(UUID empresaId);

    @Query("""
        SELECT b FROM Boleto b WHERE b.empresa.id = :empresaId
        AND (:lojaId IS NULL OR b.loja.id = :lojaId)
        AND (:status IS NULL OR b.status = :status)
        AND (CAST(:de AS date) IS NULL OR b.vencimento >= CAST(:de AS date))
        AND (CAST(:ate AS date) IS NULL OR b.vencimento <= CAST(:ate AS date))
        AND (:fornecedor IS NULL OR LOWER(b.fornecedor) LIKE :fornecedor)
        ORDER BY b.vencimento ASC
    """)
    Page<Boleto> listar(
            @Param("empresaId") UUID empresaId,
            @Param("lojaId") UUID lojaId,
            @Param("status") StatusBoleto status,
            @Param("de") LocalDate de,
            @Param("ate") LocalDate ate,
            @Param("fornecedor") String fornecedor,
            Pageable pageable
    );

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

    @Query("SELECT b FROM Boleto b WHERE b.empresa.id = :empresaId AND b.status = 'PENDENTE' AND b.vencimento = :data")
    List<Boleto> findPendentesVencendoEm(@Param("empresaId") UUID empresaId, @Param("data") LocalDate data);

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