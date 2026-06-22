package com.agenda.domain.empresa;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

/**
 * Repository de Empresa.
 * A query findBySolicitouExclusaoTrueAndExcluidoEmIsNull é usada pelo
 * job agendado que efetiva a exclusão lógica após período de carência.
 */
public interface EmpresaRepository extends JpaRepository<Empresa, UUID> {
    List<Empresa> findBySolicitouExclusaoTrueAndExcluidoEmIsNull();
    java.util.Optional<Empresa> findByNome(String nome);
}
