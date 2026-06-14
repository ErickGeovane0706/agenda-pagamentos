package com.agenda.domain.banco;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface BancoRepository extends JpaRepository<Banco, UUID> {
    List<Banco> findByEmpresaIdOrderByNome(UUID empresaId);
}
