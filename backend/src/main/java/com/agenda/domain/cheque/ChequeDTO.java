package com.agenda.domain.cheque;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de saída para Cheque.
 * <p>
 * Inclui {@code bancoId} / {@code bancoNome} (navegação ManyToOne para
 * {@link com.agenda.domain.banco.Banco}) e {@code numeroCheque} —
 * campos exclusivos do cheque (ausentes em boleto e PIX).
 * </p>
 */
public record ChequeDTO(
    UUID id, UUID lojaId, String lojaNome, String lojaCor,
    UUID bancoId, String bancoNome,
    String fornecedor, BigDecimal valor, LocalDate vencimento,
    StatusCheque status, String numeroCheque,
    String arquivoKey, String observacoes, LocalDateTime compensadoEm, LocalDateTime criadoEm
) {
    /** Converte entidade JPA em DTO — banco pode ser null (cheque sem banco cadastrado). */
    public static ChequeDTO from(Cheque c) {
        return new ChequeDTO(
            c.getId(), c.getLoja().getId(), c.getLoja().getNome(), c.getLoja().getCor(),
            c.getBanco() != null ? c.getBanco().getId() : null,
            c.getBanco() != null ? c.getBanco().getNome() : null,
            c.getFornecedor(), c.getValor(), c.getVencimento(), c.getStatus(),
            c.getNumeroCheque(), c.getArquivoKey(), c.getObservacoes(), c.getCompensadoEm(), c.getCriadoEm()
        );
    }
}
