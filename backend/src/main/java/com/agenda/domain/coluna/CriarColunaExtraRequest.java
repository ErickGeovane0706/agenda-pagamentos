package com.agenda.domain.coluna;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request de criação de coluna extra. tipoDado, obrigatorio e ordem
 * são opcionais — têm valores default na entidade.
 */
public record CriarColunaExtraRequest(
    @NotNull TipoPagamento tipoPagamento,
    @NotBlank @Size(max = 100) String nome,
    TipoDado tipoDado,
    Boolean obrigatorio,
    Integer ordem
) {}