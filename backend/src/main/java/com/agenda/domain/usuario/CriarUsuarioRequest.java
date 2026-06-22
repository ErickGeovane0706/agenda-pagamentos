package com.agenda.domain.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request de criação de usuário. Senha deve ter 8+ caracteres
 * com maiúscula, minúscula e número (validado pelo PasswordValidator).
 */
public record CriarUsuarioRequest(
    @NotBlank @Size(min = 2) String nome,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8) String senha,
    @NotNull PerfilUsuario perfil
) {}
