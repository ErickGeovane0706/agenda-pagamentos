package com.agenda.domain.banco;

import java.util.UUID;

/**
 * DTO público de banco. Exclui a referência à entidade Empresa
 * para evitar exposição desnecessária e lazy-loading no serializador.
 */
public record BancoDTO(UUID id, String nome, String codigo, boolean ativo) {
    public static BancoDTO from(Banco b) {
        return new BancoDTO(b.getId(), b.getNome(), b.getCodigo(), b.getAtivo());
    }
}
