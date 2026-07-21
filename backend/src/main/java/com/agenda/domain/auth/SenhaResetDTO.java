package com.agenda.domain.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTOs do fluxo de redefinição de senha.
 *
 * O tamanho mínimo da nova senha NÃO é validado aqui: quem manda é o
 * PasswordValidator (regra única, compartilhada com a criação de usuário).
 * Duplicar a regra no DTO faria as duas divergirem na próxima mudança.
 */
public class SenhaResetDTO {

    /** Pedido de link de redefinição. */
    public record Solicitacao(@NotBlank @Email String email) {}

    /** Redefinição efetiva, com o token que veio no link do email. */
    public record Redefinicao(@NotBlank String token, @NotBlank String novaSenha) {}

    private SenhaResetDTO() {}
}
