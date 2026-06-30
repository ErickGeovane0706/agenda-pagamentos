package com.agenda.domain.whatsappagent;

/**
 * Tipo de pagamento que o cliente mencionou na mensagem, ou {@code null}
 * (campo correspondente no DTO) quando ele não especificou e a pergunta
 * deve abranger os três tipos (Boleto + PIX + Cheque), igual ao
 * comportamento padrão do {@code NotificacaoWhatsAppScheduler}.
 */
public enum TipoPagamentoAgente {
    BOLETO, PIX, CHEQUE
}
