package com.agenda.domain.pix;

import jakarta.validation.constraints.NotNull;

/**
 * DTO de entrada para alterar o status de um PIX.
 * <p>
 * Análogo a {@link com.agenda.domain.boleto.MudarStatusRequest}.
 * </p>
 */
public record MudarStatusPixRequest(@NotNull StatusPix status) {}
