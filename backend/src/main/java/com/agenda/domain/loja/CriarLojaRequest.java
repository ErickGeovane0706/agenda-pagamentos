package com.agenda.domain.loja;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarLojaRequest(
    @NotBlank @Size(min = 2) String nome,
    String cnpj,
    String descricao,
    String cor
) {}
