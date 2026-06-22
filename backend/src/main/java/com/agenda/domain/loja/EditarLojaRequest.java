package com.agenda.domain.loja;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request de edição de loja. cor opcional — mantém a atual
 * se não for informada.
 */
public record EditarLojaRequest(
    @NotBlank @Size(min = 2) String nome,
    String cnpj,
    String descricao,
    String cor
) {}
