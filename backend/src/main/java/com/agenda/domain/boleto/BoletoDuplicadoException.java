package com.agenda.domain.boleto;

/**
 * O código do boleto já está cadastrado na empresa (HTTP 409).
 * <p>
 * É um aviso, não uma proibição: o front mostra a mensagem e, se o usuário
 * confirmar, reenvia com {@code confirmarDuplicado = true}.
 */
public class BoletoDuplicadoException extends RuntimeException {
    public BoletoDuplicadoException(String message) {
        super(message);
    }
}
