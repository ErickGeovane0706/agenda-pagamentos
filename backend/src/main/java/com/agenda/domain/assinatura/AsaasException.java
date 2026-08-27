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

    /**
     * Guarda a exceção HTTP original como causa. Ela não é exibida ao cliente —
     * serve para o chamador distinguir um 400 (o gateway recusou, nada foi
     * criado) de uma falha de rede (pode ter sido criado), decisão que muda se é
     * seguro repetir a requisição.
     */
    public AsaasException(String message, Throwable cause) {
        super(message, cause);
    }
}
