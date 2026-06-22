package com.agenda.domain.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository para RefreshToken.
 *
 * findByTokenAndRevokedFalse: busca token não revogado (válido).
 * revokeAllByUsuarioId: invalida em lote todos os tokens ativos
 * de um usuário (usado no logout e rotação).
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.usuarioId = :usuarioId AND rt.revoked = false")
    void revokeAllByUsuarioId(UUID usuarioId);
}
