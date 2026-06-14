package com.agenda.domain.banco;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarBancoRequest(
    @NotBlank @Size(min = 2) String nome,
    String codigo
) {}
