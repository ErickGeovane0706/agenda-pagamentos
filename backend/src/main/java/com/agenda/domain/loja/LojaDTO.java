package com.agenda.domain.loja;

import java.util.UUID;

public record LojaDTO(
    UUID id,
    String nome,
    String cnpj,
    String descricao,
    String cor,
    boolean ativo
) {
    public static LojaDTO from(Loja l) {
        return new LojaDTO(l.getId(), l.getNome(), l.getCnpj(), l.getDescricao(), l.getCor(), l.getAtivo());
    }
}
