package com.agenda.domain.auth;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.domain.usuario.Usuario;
import com.agenda.domain.usuario.UsuarioDTO;
import com.agenda.domain.usuario.UsuarioRepository;
import com.agenda.security.JwtService;
import com.agenda.security.RefreshTokenService;
import com.agenda.security.TokenBlacklistService;
import com.agenda.shared.UserContext;
import com.agenda.shared.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final AuthenticationManager authenticationManager;
    private final AuditoriaService auditoriaService;

    @Transactional
    public AuthLoginResult login(AuthDTO dto) {
        var email = dto.email();
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, dto.senha()));
        } catch (Exception e) {
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

    @Transactional
    public AuthLoginResult refresh(String rawRefreshToken) {
        var rotated = refreshTokenService.rotate(rawRefreshToken);
        var usuario = usuarioRepository.findById(rotated.getUsuarioId())
            .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));

        var token = jwtService.generateToken(usuario);
        return new AuthLoginResult(token, rotated.getToken(), UsuarioDTO.from(usuario));
    }

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

    public UsuarioDTO me() {
        var usuarioId = UserContext.getUsuarioId();
        var usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
        return UsuarioDTO.from(usuario);
    }

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
