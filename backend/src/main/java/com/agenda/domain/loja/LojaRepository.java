package com.agenda.domain.loja;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

/**
 * Repository de Loja. A listagem é sempre filtrada por empresa
 * (isolamento multi-tenant) e segue a ordem escolhida pelo cliente,
 * com o nome desempatando.
 */
public interface LojaRepository extends JpaRepository<Loja, UUID> {
    List<Loja> findByEmpresaIdOrderByOrdemAscNomeAsc(UUID empresaId);
    long countByEmpresaId(UUID empresaId);
}
