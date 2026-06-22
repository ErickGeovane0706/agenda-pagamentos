package com.agenda.domain.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de login: email (obrigatório, formato email) e senha
 * (obrigatório, mínimo 6 caracteres).
 */
public record AuthDTO(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 6) String senha
) {}
