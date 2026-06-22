package com.agenda.domain.cheque;

import jakarta.validation.constraints.NotNull;

/**
 * DTO de entrada para alterar o status de um Cheque.
 * <p>
 * Aceita qualquer {@link StatusCheque}: COMPENSADO, DEVOLVIDO ou CANCELADO.
 * </p>
 */
public record MudarStatusChequeRequest(@NotNull StatusCheque status) {}
