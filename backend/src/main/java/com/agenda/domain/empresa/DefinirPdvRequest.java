package com.agenda.domain.empresa;

import jakarta.validation.constraints.NotNull;

/** Liga ou desliga o módulo de Estoque/PDV da empresa. Só o MASTER usa. */
public record DefinirPdvRequest(@NotNull Boolean habilitado) {}
