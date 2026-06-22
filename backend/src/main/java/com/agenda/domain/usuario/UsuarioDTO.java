package com.agenda.domain.usuario;

import com.agenda.domain.empresa.Empresa;
import java.util.UUID;

/**
 * DTO de usuário. Inclui id da empresa e nome para exibição
 * em listas e combos no frontend.
 */
public record UsuarioDTO(
    UUID id,
    String nome,
    String email,
    PerfilUsuario perfil,
    UUID empresaId,
    String empresaNome
) {
    public static UsuarioDTO from(Usuario u) {
        return new UsuarioDTO(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(),
            u.getEmpresa().getId(), u.getEmpresa().getNome());
    }
}
