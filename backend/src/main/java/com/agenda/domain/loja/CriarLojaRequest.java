package com.agenda.domain.loja;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request de criação de loja. cor é opcional — se omitida,
 * o service usa o azul padrão (#3B82F6).
 */
public record CriarLojaRequest(
    @NotBlank @Size(min = 2) String nome,
    String cnpj,
    String descricao,
    String cor
) {}
