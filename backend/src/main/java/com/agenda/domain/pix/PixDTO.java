package com.agenda.domain.pix;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de saída para pagamento PIX.
 * <p>
 * Inclui {@code chavePix} e {@code tipoChave} (campos específicos do PIX,
 * ausentes em {@link com.agenda.domain.boleto.BoletoDTO}).
 * </p>
 */
public record PixDTO(
    UUID id, UUID lojaId, String lojaNome, String lojaCor,
    String fornecedor, BigDecimal valor, LocalDate vencimento,
    StatusPix status, String chavePix, String tipoChave,
    String arquivoKey, String observacoes, LocalDateTime pagoEm, LocalDateTime criadoEm
) {
    /** Converte entidade JPA em DTO — navega loja para extrair id, nome e cor. */
    public static PixDTO from(PagamentoPix p) {
        return new PixDTO(
            p.getId(), p.getLoja().getId(), p.getLoja().getNome(), p.getLoja().getCor(),
            p.getFornecedor(), p.getValor(), p.getVencimento(), p.getStatus(),
            p.getChavePix(), p.getTipoChave(), p.getArquivoKey(), p.getObservacoes(), p.getPagoEm(), p.getCriadoEm()
        );
    }
}
