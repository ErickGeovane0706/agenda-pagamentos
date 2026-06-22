package com.agenda.domain.boleto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de saída para boleto.
 * <p>
 * Projeta os campos da entidade {@link Boleto} + dados da loja
 * (nome e cor) para exibição nas listas do frontend.
 * </p>
 */
public record BoletoDTO(
    UUID id, UUID lojaId, String lojaNome, String lojaCor,
    String fornecedor, BigDecimal valor, LocalDate vencimento,
    StatusBoleto status, String codigoBarras, String urlArquivo,
    String nomeArquivo, String arquivoKey, String observacoes, LocalDateTime pagoEm,
    LocalDateTime criadoEm
) {
    /** Converte entidade JPA em DTO — navega loja para extrair id, nome e cor. */
    public static BoletoDTO from(Boleto b) {
        return new BoletoDTO(
            b.getId(), b.getLoja().getId(), b.getLoja().getNome(), b.getLoja().getCor(),
            b.getFornecedor(), b.getValor(), b.getVencimento(), b.getStatus(),
            b.getCodigoBarras(), b.getUrlArquivo(), b.getNomeArquivo(), b.getArquivoKey(),
            b.getObservacoes(), b.getPagoEm(), b.getCriadoEm()
        );
    }
}
