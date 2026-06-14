package com.agenda.domain.cheque;

import jakarta.validation.constraints.NotNull;

public record MudarStatusChequeRequest(@NotNull StatusCheque status) {}
