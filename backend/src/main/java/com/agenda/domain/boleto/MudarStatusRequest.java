package com.agenda.domain.boleto;

import jakarta.validation.constraints.NotNull;

/**
 * DTO de entrada para alterar o status de um boleto.
 * <p>
 * Apenas o campo {@code status} é enviado; o service decide
 * se a transição é válida e atualiza {@code pagoEm} quando
 * o novo status é PAGO.
 * </p>
 */
public record MudarStatusRequest(@NotNull StatusBoleto status) {}
