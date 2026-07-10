package com.agenda.domain.usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository de Usuario. findByEmail é usado pelo
 * UserDetailsServiceImpl na autenticação; findByEmpresaId
 * é usado pelo master para listar usuários de um tenant.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    Optional<Usuario> findByEmail(String email);
    List<Usuario> findByEmpresaId(UUID empresaId);

    /** Usuários que pediram exclusão individual e ainda não foram anonimizados (job 03:00). */
    List<Usuario> findBySolicitouExclusaoTrueAndExcluidoEmIsNull();

    /** Conta administradores ativos (não excluídos, sem pedido de exclusão) de uma empresa. */
    long countByEmpresaIdAndPerfilAndSolicitouExclusaoFalseAndExcluidoEmIsNull(UUID empresaId, PerfilUsuario perfil);
}
