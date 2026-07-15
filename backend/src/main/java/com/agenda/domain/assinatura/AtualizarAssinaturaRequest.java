package com.agenda.domain.assinatura;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AtualizarAssinaturaRequest(
    @NotNull StatusAssinatura status,
    @NotNull @Min(1) Integer lojasContratadas,
    LocalDate vigenteAte
) {}
