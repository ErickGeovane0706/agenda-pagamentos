package com.agenda.domain.assinatura;

import java.time.LocalDate;
import java.util.UUID;

public record AssinaturaDTO(
    UUID id,
    UUID empresaId,
    String empresaNome,
    StatusAssinatura status,
    int lojasContratadas,
    LocalDate vigenteAte
) {
    public static AssinaturaDTO from(Assinatura a) {
        return new AssinaturaDTO(
            a.getId(),
            a.getEmpresa().getId(),
            a.getEmpresa().getNome(),
            a.getStatus(),
            a.getLojasContratadas(),
            a.getVigenteAte());
    }
}
