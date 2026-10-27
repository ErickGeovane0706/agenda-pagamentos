package com.agenda.domain.banco;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request de criação de banco.
 * nome é obrigatório (mín. 2 caracteres); código é opcional
 * (ex.: código do banco no sistema de compensação). Agência e conta
 * também são opcionais e livres (com dígito, ex.: "45678-X").
 */
public record CriarBancoRequest(
    @NotBlank @Size(min = 2) String nome,
    String codigo,
    @Size(max = 10) String agencia,
    @Size(max = 20) String conta
) {}
