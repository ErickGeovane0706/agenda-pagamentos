package com.agenda.domain.empresa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request de edição de empresa. Permite alterar nome e ativar/
 * desativar o tenant. A desativação impede novos acessos.
 */
public record EditarEmpresaRequest(
    @NotBlank @Size(min = 2, max = 200) String nome,
    boolean ativo
) {}
