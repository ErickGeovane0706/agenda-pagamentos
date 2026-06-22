package com.agenda.domain.coluna;

import java.util.UUID;

/**
 * DTO de coluna extra. Exclui empresaId por segurança.
 */
public record ColunaExtraDTO(
    UUID id,
    TipoPagamento tipoPagamento,
    String nome,
    TipoDado tipoDado,
    boolean obrigatorio,
    int ordem,
    boolean ativo
) {
    public static ColunaExtraDTO from(ColunaExtra c) {
        return new ColunaExtraDTO(c.getId(), c.getTipoPagamento(), c.getNome(),
            c.getTipoDado(), c.getObrigatorio(), c.getOrdem(), c.getAtivo());
    }
}