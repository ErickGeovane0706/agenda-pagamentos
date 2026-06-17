package com.agenda.security;

import com.agenda.config.JwtConfig;
import com.agenda.domain.auth.RefreshToken;
import com.agenda.domain.auth.RefreshTokenRepository;
import com.agenda.domain.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtConfig jwtConfig;

    @Transactional
    public RefreshToken create(Usuario usuario) {
        var entity = RefreshToken.builder()
            .usuarioId(usuario.getId())
            .token(UUID.randomUUID().toString())
            .expiresAt(LocalDateTime.now().plus(Duration.ofMillis(jwtConfig.getRefreshExpiration())))
            .build();
        return refreshTokenRepository.save(entity);
    }

    @Transactional
    public RefreshToken rotate(String rawToken) {
        var existing = refreshTokenRepository.findByTokenAndRevokedFalse(rawToken)
            .orElseThrow(() -> new RuntimeException("Refresh token invalido ou revogado"));

        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            existing.setRevoked(true);
            refreshTokenRepository.save(existing);
            throw new RuntimeException("Refresh token expirado");
        }

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        var novo = RefreshToken.builder()
            .usuarioId(existing.getUsuarioId())
            .token(UUID.randomUUID().toString())
            .expiresAt(LocalDateTime.now().plus(Duration.ofMillis(jwtConfig.getRefreshExpiration())))
            .build();
        return refreshTokenRepository.save(novo);
    }

    @Transactional
    public void revokeAll(UUID usuarioId) {
        refreshTokenRepository.revokeAllByUsuarioId(usuarioId);
    }
}
