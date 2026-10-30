package com.agenda.domain.cheque;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de saída para Cheque.
 * <p>
 * Inclui {@code bancoId} / {@code bancoNome} / agência / conta (navegação ManyToOne para
 * {@link com.agenda.domain.banco.Banco}) e {@code numeroCheque} —
 * campos exclusivos do cheque (ausentes em boleto e PIX).
 * </p>
 */
public record ChequeDTO(
    UUID id, UUID lojaId, String lojaNome, String lojaCor,
    UUID bancoId, String bancoNome, String bancoAgencia, String bancoConta,
    String fornecedor, BigDecimal valor, LocalDate vencimento,
    StatusCheque status, String numeroCheque,
    int qtdAnexos, String observacoes, LocalDateTime compensadoEm, LocalDateTime criadoEm,
    String notaFiscalPedido
) {
    /** Converte entidade JPA em DTO — banco pode ser null (cheque sem banco cadastrado). */
    public static ChequeDTO from(Cheque c) {
        return new ChequeDTO(
            c.getId(), c.getLoja().getId(), c.getLoja().getNome(), c.getLoja().getCor(),
            c.getBanco() != null ? c.getBanco().getId() : null,
            c.getBanco() != null ? c.getBanco().getNome() : null,
            c.getBanco() != null ? c.getBanco().getAgencia() : null,
            c.getBanco() != null ? c.getBanco().getConta() : null,
            c.getFornecedor(), c.getValor(), c.getVencimento(), c.getStatus(),
            c.getNumeroCheque(), c.getQtdAnexos(), c.getObservacoes(), c.getCompensadoEm(), c.getCriadoEm(),
            c.getNotaFiscalPedido()
        );
    }
}
