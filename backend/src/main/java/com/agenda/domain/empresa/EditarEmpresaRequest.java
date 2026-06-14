package com.agenda.domain.empresa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditarEmpresaRequest(
    @NotBlank @Size(min = 2, max = 200) String nome,
    boolean ativo
) {}
