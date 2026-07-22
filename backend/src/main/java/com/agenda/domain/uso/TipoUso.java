package com.agenda.domain.uso;

/**
 * Tipos de consumo medidos por empresa. Cada um custa dinheiro a um
 * fornecedor diferente, por isso são contados (e limitados) separadamente.
 */
public enum TipoUso {
    /** Lembrete enviado pelo scheduler — mensagem de template cobrada pela Meta. */
    TEMPLATE,

    /** Pergunta respondida pelo agente — chamada paga à Anthropic. */
    AGENTE,

    /** Nota de voz transcrita — chamada paga à OpenAI. */
    AUDIO,

    /**
     * Não é consumo: é o controle de "já avisei esta empresa neste mês".
     * Mora aqui porque o teto de 1 por competência dá exatamente a semântica
     * de avisar uma única vez, reusando o mesmo UPSERT atômico — sem coluna
     * nem fluxo próprio.
     */
    AVISO_LIMITE
}
