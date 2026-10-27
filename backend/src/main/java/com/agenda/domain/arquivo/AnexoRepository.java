package com.agenda.domain.arquivo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnexoRepository extends JpaRepository<Anexo, UUID> {
    List<Anexo> findByBoletoIdOrderByCriadoEmAsc(UUID boletoId);
    List<Anexo> findByPixIdOrderByCriadoEmAsc(UUID pixId);
    List<Anexo> findByChequeIdOrderByCriadoEmAsc(UUID chequeId);
}
