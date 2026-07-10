package com.agenda.domain.auth;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.domain.usuario.Usuario;
import com.agenda.domain.usuario.UsuarioDTO;
import com.agenda.domain.usuario.UsuarioRepository;
import com.agenda.security.JwtService;
import com.agenda.security.RateLimiterService;
import com.agenda.security.RefreshTokenService;
import com.agenda.security.TokenBlacklistService;
import com.agenda.shared.UserContext;
import com.agenda.shared.exception.NotFoundException;
import com.agenda.shared.exception.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * Lógica de autenticação e gerenciamento de sessão.
 * Login gera access token (JWT) + refresh token; logout
 * invalida o refresh e blacklista o access. Troca de empresa
 * (switch) gera novo JWT com tenant atualizado.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final AuthenticationManager authenticationManager;
    private final AuditoriaService auditoriaService;
    private final RateLimiterService rateLimiter;

    /**
     * Teto de tentativas de login por EMAIL: 10 falhas a cada 15 minutos.
     * Keado por email (não por IP) para ser infalsificável e mirar o alvo
     * real — brute-force contra uma conta específica. Só uma tentativa que
     * FALHA consome um token, então quem digita a senha certa nunca é
     * bloqueado (evita o "lockout DoS" de trancar a conta de outra pessoa).
     */
    private static final int LOGIN_MAX_FALHAS = 10;
    private static final Duration LOGIN_JANELA = Duration.ofMinutes(15);

    /**
     * Realiza login do usuário.
     *
     * Regras de negócio:
     * 1. AuthenticationManager valida as credenciais (lança exceção se inválidas)
     * 2. Se a autenticação falhar, registra auditoria LOGIN_FALHA (sem ID,
     *    pois o usuário pode nem existir)
     * 3. Usuário inativo (ativo=false) não pode logar — registra LOGIN_FALHA
     *    com motivo "inativo"
     * 4. Sucesso: gera access token JWT, cria refresh token, registra LOGIN
     */
    @Transactional
    public AuthLoginResult login(AuthDTO dto) {
        var email = dto.email();
        var chaveRateLimit = "login:" + email.toLowerCase();

        if (!rateLimiter.temTokenDisponivel(chaveRateLimit, LOGIN_MAX_FALHAS, LOGIN_JANELA)) {
            auditoriaService.registrar("LOGIN_FALHA", "USUARIO", null, "Email: " + email + " (rate limit)");
            throw new TooManyRequestsException("Muitas tentativas de login. Tente novamente em alguns minutos.");
        }

        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, dto.senha()));
        } catch (Exception e) {
            rateLimiter.tentarConsumir(chaveRateLimit, LOGIN_MAX_FALHAS, LOGIN_JANELA);
            auditoriaService.registrar("LOGIN_FALHA", "USUARIO", null, "Email: " + email);
            throw e;
        }

        var usuario = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));

        if (!usuario.getAtivo()) {
            auditoriaService.registrar("LOGIN_FALHA", "USUARIO", usuario.getId(), "Email: " + email + " (inativo)");
            throw new NotFoundException("Usuário inativo");
        }

        var token = jwtService.generateToken(usuario);
        var refreshToken = refreshTokenService.create(usuario);
        auditoriaService.registrar("LOGIN", "USUARIO", usuario.getId(), "Email: " + email);
        return new AuthLoginResult(token, refreshToken.getToken(), UsuarioDTO.from(usuario));
    }

    /**
     * Renova o access token usando refresh token rotation.
     * O refresh token antigo é revogado e um novo é emitido
     * (rotação automática por segurança).
     */
    @Transactional
    public AuthLoginResult refresh(String rawRefreshToken) {
        var rotated = refreshTokenService.rotate(rawRefreshToken);
        var usuario = usuarioRepository.findById(rotated.getUsuarioId())
            .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));

        var token = jwtService.generateToken(usuario);
        return new AuthLoginResult(token, rotated.getToken(), UsuarioDTO.from(usuario));
    }

    /**
     * Logout: adiciona o access token atual à blacklist (impede uso
     * até expirar) e revoga todos os refresh tokens do usuário.
     */
    @Transactional
    public void logout(HttpServletRequest request) {
        var token = extractJwtFromRequest(request);
        if (token != null) {
            var jti = jwtService.extractJti(token);
            if (jti != null) {
                tokenBlacklistService.add(jti, jwtService.extractExpiration(token));
            }
        }
        var usuarioId = UserContext.getUsuarioId();
        if (usuarioId != null) {
            refreshTokenService.revokeAll(usuarioId);
            auditoriaService.registrar("LOGOUT", "USUARIO", usuarioId, null);
        }
    }

    /**
     * Retorna os dados do usuário autenticado a partir do contexto
     * da requisição.
     */
    public UsuarioDTO me() {
        var usuarioId = UserContext.getUsuarioId();
        var usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
        return UsuarioDTO.from(usuario);
    }

    /**
     * Extrai o JWT do header Authorization (Bearer) ou do cookie "jwt".
     * O cookie é prioritário para navegadores; o header para clients
     * como Postman/API.
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) return authHeader.substring(7);
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if ("jwt".equals(cookie.getName())) return cookie.getValue();
            }
        }
        return null;
    }
}
