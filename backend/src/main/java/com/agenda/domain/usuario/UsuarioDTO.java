package com.agenda.domain.usuario;

import com.agenda.domain.empresa.Empresa;
import java.util.UUID;

/**
 * DTO de usuário. Inclui id da empresa e nome para exibição
 * em listas e combos no frontend.
 * <p>
 * {@code onboardingEtapa} viaja no login e no /me porque o frontend decide por
 * ele se manda o usuário para o wizard de boas-vindas ou direto para o app —
 * é o que faz o onboarding retomar de onde parou em vez de recomeçar.
 */
public record UsuarioDTO(
    UUID id,
    String nome,
    String email,
    PerfilUsuario perfil,
    UUID empresaId,
    String empresaNome,
    EtapaOnboarding onboardingEtapa
) {
    public static UsuarioDTO from(Usuario u) {
        return new UsuarioDTO(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(),
            u.getEmpresa().getId(), u.getEmpresa().getNome(), u.getOnboardingEtapa());
    }
}
