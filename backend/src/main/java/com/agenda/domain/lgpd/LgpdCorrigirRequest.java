package com.agenda.domain.lgpd;

import jakarta.validation.constraints.NotBlank;

/**
 * Request de solicitação de correção de dados pessoais (LGPD Art. 18, III).
 * O usuário informa qual campo está incorreto e os valores atual/corrigido;
 * o sistema registra em auditoria para ação do suporte.
 */
public record LgpdCorrigirRequest(
    @NotBlank String campo,
    @NotBlank String valorAtual,
    @NotBlank String valorCorrigido
) {}
