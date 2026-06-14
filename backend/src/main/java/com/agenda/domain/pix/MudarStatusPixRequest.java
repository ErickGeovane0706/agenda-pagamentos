package com.agenda.domain.pix;

import jakarta.validation.constraints.NotNull;

public record MudarStatusPixRequest(@NotNull StatusPix status) {}
