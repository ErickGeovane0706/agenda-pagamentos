package com.agenda.domain.empresa;

import java.util.UUID;

/**
 * DTO público de empresa. Expõe apenas dados básicos,
 * ocultando flags internas de exclusão e timestamps.
 */
public record EmpresaDTO(
    UUID id,
    String nome,
    boolean ativo
) {
    public static EmpresaDTO from(Empresa e) {
        return new EmpresaDTO(e.getId(), e.getNome(), e.getAtivo());
    }
}
