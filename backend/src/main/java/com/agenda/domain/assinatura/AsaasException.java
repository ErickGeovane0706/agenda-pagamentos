package com.agenda.domain.assinatura;

/**
 * Falha na integração com a API do Asaas. A mensagem já vem sanitizada para
 * poder ser exibida ao cliente — nunca carrega api-key, CPF/CNPJ ou o corpo
 * bruto da resposta do gateway.
 */
public class AsaasException extends RuntimeException {
    public AsaasException(String message) {
        super(message);
    }
}
