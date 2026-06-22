package com.agenda.domain.empresa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request de criação de empresa. Apenas o nome é obrigatório;
 * a entidade inicializa ativo=true por padrão.
 */
public record CriarEmpresaRequest(
    @NotBlank @Size(min = 2, max = 200) String nome
) {}
