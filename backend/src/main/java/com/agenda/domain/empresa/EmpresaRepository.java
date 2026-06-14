package com.agenda.domain.empresa;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface EmpresaRepository extends JpaRepository<Empresa, UUID> {
    List<Empresa> findBySolicitouExclusaoTrueAndExcluidoEmIsNull();
    java.util.Optional<Empresa> findByNome(String nome);
}
