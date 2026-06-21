package com.agenda.domain.notificacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Representação unificada de um Boleto, PIX ou Cheque para fins de notificação.
 * Os três tipos têm os mesmos campos relevantes (fornecedor, valor, vencimento, loja),
 * então em vez de triplicar a lógica de montagem de mensagem, eles são convertidos
 * para este tipo único antes de montar o texto do WhatsApp.
 */
public record PendenciaNotificacao(
        TipoPendencia tipo,
        UUID lojaId,
        String lojaNome,
        String fornecedor,
        BigDecimal valor,
        LocalDate vencimento
) {
    public enum TipoPendencia {
        BOLETO, PIX, CHEQUE
    }

    public long diasAtraso(LocalDate hoje) {
        return java.time.temporal.ChronoUnit.DAYS.between(vencimento, hoje);
    }
}