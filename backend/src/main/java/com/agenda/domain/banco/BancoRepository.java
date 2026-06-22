package com.agenda.domain.banco;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

/**
 * Repository para a entidade Banco.
 *
 * findByEmpresaIdOrderByNome: consulta isolada por tenant — empresas
 * diferentes enxergam apenas seus próprios bancos.
 */
public interface BancoRepository extends JpaRepository<Banco, UUID> {
    List<Banco> findByEmpresaIdOrderByNome(UUID empresaId);
}
