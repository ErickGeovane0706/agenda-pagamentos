package com.agenda.domain.banco;

import java.util.UUID;

public record BancoDTO(UUID id, String nome, String codigo, boolean ativo) {
    public static BancoDTO from(Banco b) {
        return new BancoDTO(b.getId(), b.getNome(), b.getCodigo(), b.getAtivo());
    }
}
