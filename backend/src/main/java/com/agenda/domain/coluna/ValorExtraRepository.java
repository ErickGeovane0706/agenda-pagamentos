package com.agenda.domain.coluna;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

/**
 * Repository de ValorExtra. A exclusão em lote (deleteBy…)
 * é usada para substituir todos os valores de um registro
 * em uma única transação (delete + insert).
 */
public interface ValorExtraRepository extends JpaRepository<ValorExtra, UUID> {
    List<ValorExtra> findByRegistroIdAndTipoPagamento(UUID registroId, TipoPagamento tipoPagamento);
    void deleteByRegistroIdAndTipoPagamento(UUID registroId, TipoPagamento tipoPagamento);
}