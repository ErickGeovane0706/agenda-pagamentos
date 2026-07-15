package com.agenda.domain.assinatura;

/**
 * Status da assinatura de uma empresa.
 *
 * TRIAL e ATIVA dão acesso normal; INADIMPLENTE e CANCELADA colocam a
 * empresa em modo somente-leitura (ver AssinaturaService.podeAcessar).
 * Não existe enum de plano: o modelo é um produto único cobrado por loja.
 */
public enum StatusAssinatura {
    TRIAL,
    ATIVA,
    INADIMPLENTE,
    CANCELADA
}
