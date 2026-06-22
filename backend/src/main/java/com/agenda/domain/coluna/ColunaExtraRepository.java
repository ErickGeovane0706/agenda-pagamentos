package com.agenda.domain.coluna;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

/**
 * Repository de ColunaExtra. A query principal filtra por
 * empresa + tipo de pagamento + ativo, ordenada por ordem.
 */
public interface ColunaExtraRepository extends JpaRepository<ColunaExtra, UUID> {
    List<ColunaExtra> findByEmpresaIdAndTipoPagamentoAndAtivoTrueOrderByOrdemAsc(UUID empresaId, TipoPagamento tipoPagamento);
}