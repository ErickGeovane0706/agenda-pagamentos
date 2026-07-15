package com.agenda.domain.assinatura;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AtualizarAssinaturaRequest(
    @NotNull StatusAssinatura status,
    @NotNull @Min(1) Integer lojasContratadas,
    LocalDate vigenteAte,
    /** Vincula a empresa ao customer do Asaas (cus_...) para o webhook correlacionar. Opcional; não limpa se ausente. */
    String gatewayCustomerId
) {}
