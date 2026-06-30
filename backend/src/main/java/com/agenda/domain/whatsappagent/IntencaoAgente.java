package com.agenda.domain.whatsappagent;

/**
 * Lista FECHADA de intenções que o agente conversacional do WhatsApp sabe
 * atender. A LLM (ver {@link IntentClassifierService}) só pode classificar
 * a mensagem do cliente como um destes valores — nunca inventa uma
 * intenção nova fora desta lista.
 * <p>
 * Separação deliberada entre LEITURA e AÇÃO: nenhum valor aqui executa
 * escrita no banco (pagar, cancelar, mudar status). Intenções que
 * pareçam pedir uma ação (ex.: "quero cancelar esse cheque") são
 * classificadas como {@link #OBTER_DADOS_CHEQUE} — o agente devolve os
 * dados para o usuário decidir/agir no sistema, nunca cancela direto a
 * partir de uma mensagem de texto.
 */
public enum IntencaoAgente {

    /**
     * Cliente quer saber quanto tem a pagar/receber, com filtros opcionais
     * de loja, tipo de pagamento e período. Ex.: "quanto vence hoje",
     * "boletos da loja centro essa semana".
     */
    CONSULTAR_PENDENCIAS,

    /**
     * Cliente quer o dado para EFETUAR um pagamento específico: código de
     * barras (boleto) ou chave PIX. Ex.: "me manda o código de barras do
     * boleto da Fornecedora X".
     */
    OBTER_DADOS_PAGAMENTO,

    /**
     * Cliente quer dados de um cheque específico (número, banco, valor,
     * status) — inclusive quando a motivação declarada é cancelamento.
     * O agente NUNCA cancela a partir desta intenção, só informa os dados.
     */
    OBTER_DADOS_CHEQUE,

    /**
     * Saudação ("oi", "bom dia") ou pergunta sobre o que o bot faz
     * ("o que você faz", "como funciona"). Resposta é um texto fixo de
     * ajuda, não consulta o banco.
     */
    SAUDACAO_AJUDA,

    /**
     * Fallback: a LLM não conseguiu mapear a mensagem para nenhuma
     * intenção acima com confiança razoável. O agente responde pedindo
     * para o cliente reformular, sem tentar adivinhar.
     */
    NAO_ENTENDIDO
}
