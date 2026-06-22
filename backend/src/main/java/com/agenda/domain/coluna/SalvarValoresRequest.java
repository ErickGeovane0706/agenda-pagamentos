package com.agenda.domain.coluna;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

/**
 * Request para salvar valores de colunas extras de um registro.
 * valores é um Map<colunaId, valor> — chaves são UUIDs das colunas.
 */
public record SalvarValoresRequest(
    @NotNull TipoPagamento tipoPagamento,
    @NotNull UUID registroId,
    Map<UUID, String> valores
) {}