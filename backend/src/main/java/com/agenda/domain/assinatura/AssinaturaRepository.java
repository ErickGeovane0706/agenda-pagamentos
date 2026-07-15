package com.agenda.domain.assinatura;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssinaturaRepository extends JpaRepository<Assinatura, UUID> {
    Optional<Assinatura> findByEmpresaId(UUID empresaId);
    Optional<Assinatura> findByGatewayCustomerId(String gatewayCustomerId);
    List<Assinatura> findByStatusInAndVigenteAteBefore(Collection<StatusAssinatura> status, LocalDate data);
}
