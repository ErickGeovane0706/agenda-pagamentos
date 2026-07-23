package com.agenda.domain.usuario;

import jakarta.validation.constraints.NotNull;

/**
 * Request para o usuário logado registrar em que ponto do onboarding está.
 * <p>
 * O cliente informa a etapa de destino (e não um "avançar") porque todo passo
 * do wizard é pulável — quem pula do primeiro direto para o fim manda
 * {@code CONCLUIDO} e o servidor não precisa saber por onde ele passou.
 */
public record AvancarOnboardingRequest(
    @NotNull EtapaOnboarding etapa
) {}
