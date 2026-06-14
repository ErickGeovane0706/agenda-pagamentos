package com.agenda.domain.coluna;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

public record SalvarValoresRequest(
    @NotNull TipoPagamento tipoPagamento,
    @NotNull UUID registroId,
    Map<UUID, String> valores
) {}