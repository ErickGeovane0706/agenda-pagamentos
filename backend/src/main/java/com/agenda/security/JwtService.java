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

/**
 * Geração e validação de tokens JWT. Access token com
 * claims: subject (email), userId, empresaId, nome, perfil.
 * Assinatura HMAC-SHA256 a partir de chave configurável.
 * Suporta blacklist (logout) e extração de claims para
 * o filtro de autenticação.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtConfig jwtConfig;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * Extrai o email (subject) do token JWT.
     * Usado pelo filtro de autenticação para carregar o UserDetails.
     */
    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Extrai o UUID da empresa (tenant) do claim "empresaId".
     * Necessário para o isolamento multi-tenant em cada requisição.
     */
    public UUID extractEmpresaId(String token) {
        var empresaId = extractAllClaims(token).get("empresaId", String.class);
        return UUID.fromString(empresaId);
    }

    /**
     * Extrai o perfil do usuário (ex.: MASTER, ADMIN, USER) do claim "perfil".
     * Usado para decisões de autorização baseadas em papel.
     */
    public String extractPerfil(String token) {
        return extractAllClaims(token).get("perfil", String.class);
    }

    /**
     * Extrai o nome do usuário do claim "nome".
     * Útil para exibição sem precisar consultar o banco novamente.
     */
    public String extractNome(String token) {
        return extractAllClaims(token).get("nome", String.class);
    }

    /**
     * Valida o token: confere se o email bate com o UserDetails,
     * se não expirou e se o JTI não está na blacklist (logout).
     */
    public boolean isValid(String token, UserDetails user) {
        var email = extractEmail(token);
        if (!email.equals(user.getUsername()) || isExpired(token)) return false;
        var jti = extractJti(token);
        return jti == null || !tokenBlacklistService.isBlacklisted(jti);
    }

    /**
     * Extrai o UUID do usuário do claim "usuarioId".
     * Identificador único do usuário autenticado, usado nas operações de domínio.
     */
    public UUID extractUsuarioId(String token) {
        var usuarioId = extractAllClaims(token).get("usuarioId", String.class);
        return UUID.fromString(usuarioId);
    }

    /**
     * Extrai o JTI (JWT ID) do token.
     * Identificador único do token, usado para blacklist de logout.
     */
    public String extractJti(String token) {
        return extractAllClaims(token).getId();
    }

    /**
     * Extrai a data de expiração do token.
     * Usado pelo TokenBlacklistService para limpeza automática de entradas expiradas.
     */
    public Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    /**
     * Gera um novo token JWT com JTI aleatório.
     * Chamado durante login.
     */
    public String generateToken(Usuario usuario) {
        var jti = UUID.randomUUID().toString();
        return generateToken(usuario, jti);
    }

    /**
     * Gera um token JWT com JTI específico (usado em refresh).
     * Contém email, usuarioId, empresaId (tenant), perfil e nome como claims.
     * Assinado com HMAC-SHA a partir da chave configurada em JwtConfig.
     */
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

    /**
     * Verifica se o token já expirou comparando a data de expiração com o momento atual.
     */
    public boolean isExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    /**
     * Faz o parsing e verificação da assinatura do token JWT,
     * retornando todos os claims contidos no payload.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /**
     * Reconstrói a chave HMAC-SHA a partir do segredo Base64 configurado.
     * O segredo deve ter tamanho suficiente para o algoritmo (>= 256 bits para HS256).
     */
    private SecretKey getSigningKey() {
        var keyBytes = Decoders.BASE64.decode(jwtConfig.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
