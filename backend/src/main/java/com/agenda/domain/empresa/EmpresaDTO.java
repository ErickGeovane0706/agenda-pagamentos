package com.agenda.domain.empresa;

import java.util.UUID;

/**
 * DTO público de empresa. Expõe apenas dados básicos,
 * ocultando flags internas de exclusão e timestamps.
 */
public record EmpresaDTO(
    UUID id,
    String nome,
    boolean ativo,

    /** Módulo de Estoque/PDV liberado. É o que o painel do MASTER desenha como interruptor. */
    boolean pdvHabilitado
) {
    public static EmpresaDTO from(Empresa e) {
        return new EmpresaDTO(e.getId(), e.getNome(), e.getAtivo(), e.getPdvHabilitado());
    }
}
