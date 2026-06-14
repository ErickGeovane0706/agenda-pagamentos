package com.agenda.domain.loja;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LojaRepository extends JpaRepository<Loja, UUID> {
    List<Loja> findByEmpresaIdOrderByNome(UUID empresaId);
}
