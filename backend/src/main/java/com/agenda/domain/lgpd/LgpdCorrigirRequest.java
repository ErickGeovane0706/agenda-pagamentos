package com.agenda.domain.lgpd;

import jakarta.validation.constraints.NotBlank;

public record LgpdCorrigirRequest(
    @NotBlank String campo,
    @NotBlank String valorAtual,
    @NotBlank String valorCorrigido
) {}
