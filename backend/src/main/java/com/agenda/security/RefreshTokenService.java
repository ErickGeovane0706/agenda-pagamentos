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

/**
 * Gerenciamento de refresh tokens. Gera tokens
 * UUID únicos com expiração configurável (jwt.refresh-expiration).
 * Suporta rotação: ao usar um refresh token, o anterior é
 * invalidado (proteção contra reuse).
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtConfig jwtConfig;

    /**
     * Cria um novo refresh token para o usuário.
     * O token é um UUID aleatório armazenado no banco, com data de expiração
     * definida pela configuração (jwt.refresh-expiration).
     */
    @Transactional
    public RefreshToken create(Usuario usuario) {
        var entity = RefreshToken.builder()
            .usuarioId(usuario.getId())
            .token(UUID.randomUUID().toString())
            .expiresAt(LocalDateTime.now().plus(Duration.ofMillis(jwtConfig.getRefreshExpiration())))
            .build();
        return refreshTokenRepository.save(entity);
    }

    /**
     * Rotaciona o refresh token (token rotation pattern):
     * 1. Localiza o token atual não revogado
     * 2. Se expirado, revoga e lança erro
     * 3. Revoga o token atual (uso único)
     * 4. Gera um novo token para o mesmo usuário
     *
     * Isso limita a janela de exposição: se um token for roubado,
     * ao ser usado pelo atacante o usuário legítimo terá seu token
     * rejeitado na próxima tentativa.
     */
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

    /**
     * Revoga todos os refresh tokens de um usuário.
     * Usado quando a senha é alterada ou quando uma conta é desativada,
     * forçando o logout de todas as sessões.
     */
    @Transactional
    public void revokeAll(UUID usuarioId) {
        refreshTokenRepository.revokeAllByUsuarioId(usuarioId);
    }
}
