package com.agenda.security;

import com.agenda.config.JwtConfig;
import com.agenda.domain.usuario.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtConfig jwtConfig;
    private final TokenBlacklistService tokenBlacklistService;

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public UUID extractEmpresaId(String token) {
        var empresaId = extractAllClaims(token).get("empresaId", String.class);
        return UUID.fromString(empresaId);
    }

    public String extractPerfil(String token) {
        return extractAllClaims(token).get("perfil", String.class);
    }

    public String extractNome(String token) {
        return extractAllClaims(token).get("nome", String.class);
    }

    public boolean isValid(String token, UserDetails user) {
        var email = extractEmail(token);
        if (!email.equals(user.getUsername()) || isExpired(token)) return false;
        var jti = extractJti(token);
        return jti == null || !tokenBlacklistService.isBlacklisted(jti);
    }

    public UUID extractUsuarioId(String token) {
        var usuarioId = extractAllClaims(token).get("usuarioId", String.class);
        return UUID.fromString(usuarioId);
    }

    public String extractJti(String token) {
        return extractAllClaims(token).getId();
    }

    public Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    public String generateToken(Usuario usuario) {
        var jti = UUID.randomUUID().toString();
        return generateToken(usuario, jti);
    }

    public String generateToken(Usuario usuario, String jti) {
        var now = new Date();
        var expiration = new Date(now.getTime() + jwtConfig.getExpiration());

        return Jwts.builder()
            .id(jti)
            .subject(usuario.getEmail())
            .claim("usuarioId", usuario.getId().toString())
            .claim("empresaId", usuario.getEmpresa().getId().toString())
            .claim("perfil", usuario.getPerfil().name())
            .claim("nome", usuario.getNome())
            .issuedAt(now)
            .expiration(expiration)
            .signWith(getSigningKey())
            .compact();
    }

    public boolean isExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private SecretKey getSigningKey() {
        var keyBytes = Decoders.BASE64.decode(jwtConfig.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
