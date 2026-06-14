package com.agenda.domain.pix;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PixDTO(
    UUID id, UUID lojaId, String lojaNome, String lojaCor,
    String fornecedor, BigDecimal valor, LocalDate vencimento,
    StatusPix status, String chavePix, String tipoChave,
    String arquivoKey, String observacoes, LocalDateTime pagoEm, LocalDateTime criadoEm
) {
    public static PixDTO from(PagamentoPix p) {
        return new PixDTO(
            p.getId(), p.getLoja().getId(), p.getLoja().getNome(), p.getLoja().getCor(),
            p.getFornecedor(), p.getValor(), p.getVencimento(), p.getStatus(),
            p.getChavePix(), p.getTipoChave(), p.getArquivoKey(), p.getObservacoes(), p.getPagoEm(), p.getCriadoEm()
        );
    }
}
