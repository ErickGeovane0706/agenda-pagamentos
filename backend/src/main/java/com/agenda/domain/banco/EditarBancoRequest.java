package com.agenda.domain.banco;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request de edição de banco.
 * Mesmas validações de CriarBancoRequest.
 */
public record EditarBancoRequest(
    @NotBlank @Size(min = 2) String nome,
    String codigo
) {}
