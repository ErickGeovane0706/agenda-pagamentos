package com.agenda.domain.banco;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request de criação de banco.
 * nome é obrigatório (mín. 2 caracteres); código é opcional
 * (ex.: código do banco no sistema de compensação).
 */
public record CriarBancoRequest(
    @NotBlank @Size(min = 2) String nome,
    String codigo
) {}
