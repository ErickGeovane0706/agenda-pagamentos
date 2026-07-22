package com.agenda.domain.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTOs do cadastro público.
 * <p>
 * <b>Não existe campo {@code perfil} aqui, e isso é a defesa.</b> Reusar
 * {@code CriarUsuarioRequest} — que tem {@code perfil} — permitiria a qualquer
 * visitante pedir MASTER pelo JSON. O primeiro usuário é sempre ADMIN, fixado no
 * servidor.
 * <p>
 * O tamanho mínimo da senha NÃO é validado aqui: quem manda é o
 * {@code PasswordValidator}, regra única compartilhada com a criação de usuário.
 * Duplicar faria as duas divergirem na próxima mudança.
 */
public class RegistroDTO {

    /**
     * Pedido de cadastro.
     *
     * @param nomeNegocio  vira o nome da empresa E o da primeira loja — para dono
     *                     de loja pequena os dois são a mesma coisa, então o
     *                     formulário pergunta uma vez só
     * @param turnstileToken token do widget anti-bot; ausente é aceito enquanto a
     *                     secret não estiver configurada (ver TurnstileValidator)
     * @param website      honeypot: campo escondido por CSS que humano nunca
     *                     preenche. Bot que preenche é descartado em silêncio.
     *                     Chama-se "website" porque é o rótulo que os
     *                     preenchedores automáticos mais reconhecem
     */
    public record Solicitacao(
            @NotBlank @Size(min = 2, max = 200) String nomeNegocio,
            @NotBlank @Size(min = 2, max = 200) String nome,
            @NotBlank @Email @Size(max = 200) String email,
            @NotBlank String senha,
            @AssertTrue(message = "É necessário aceitar os termos de uso") boolean aceiteTermos,
            String turnstileToken,
            String website
    ) {}

    /** Confirmação do email, com o token que veio no link. */
    public record Confirmacao(@NotBlank String token) {}

    private RegistroDTO() {}
}
