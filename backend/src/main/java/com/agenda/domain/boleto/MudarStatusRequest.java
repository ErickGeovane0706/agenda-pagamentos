package com.agenda.domain.boleto;

import jakarta.validation.constraints.NotNull;

public record MudarStatusRequest(@NotNull StatusBoleto status) {}
