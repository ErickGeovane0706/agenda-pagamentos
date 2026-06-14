package com.agenda.domain.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditarUsuarioRequest(
    @NotBlank @Size(min = 2) String nome,
    @NotBlank @Email String email,
    @Size(min = 8) String senha
) {}