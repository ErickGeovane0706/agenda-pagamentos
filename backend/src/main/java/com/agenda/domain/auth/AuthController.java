package com.agenda.domain.auth;

import com.agenda.domain.usuario.UsuarioDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * Controller de autenticação. Opera com cookies httpOnly
 * (jwt + refresh-token) em vez de Bearer header, seguindo
 * boas práticas para SPAs. Login devolve o usuário e um
 * cookie JWT; switch-troca de empresa gera novo JWT com
 * o tenant correto sem reautenticar.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SenhaResetService senhaResetService;
    private final RegistroService registroService;

    /**
     * Flag {@code Secure} dos cookies de auth. Default {@code true} (produção,
     * borda HTTPS do Railway); sobrescrito para {@code false} no perfil local
     * (dev em http://localhost).
     */
    @Value("${security.cookie-secure:true}")
    private boolean cookieSecure;

    /**
     * Autentica o usuário por email+senha.
     * Em caso de sucesso, define os cookies httpOnly:
     * - "jwt" (access token) — escopo /api, dura 24h
     * - "refresh_token" — escopo /api/auth/refresh, dura 7 dias
     * Retorna os dados do usuário no body.
     * Endpoint público (SecurityConfig permite sem autenticação).
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid AuthDTO dto,
                                                HttpServletResponse response) {
        var result = authService.login(dto);
        setAuthCookies(response, result);
        return ResponseEntity.ok(new LoginResponse(result.usuario()));
    }

    /**
     * Renova o access token usando o refresh token armazenado em cookie.
     * A rota é pública pois o refresh token precisa ser enviado sem
     * um access token válido.
     * Se o cookie não existir, retorna 401.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest request,
                                         HttpServletResponse response) {
        var rawRefreshToken = extractRefreshToken(request);
        if (rawRefreshToken == null) {
            return ResponseEntity.status(401).build();
        }
        var result = authService.refresh(rawRefreshToken);
        setAuthCookies(response, result);
        return ResponseEntity.ok().build();
    }

    /**
     * Invalida o access token (blacklist) e revoga todos os refresh tokens
     * do usuário. Remove os cookies do cliente.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request,
                                        HttpServletResponse response) {
        authService.logout(request);
        clearAuthCookies(response);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retorna os dados do usuário autenticado (baseado no contexto
     * populado pelo JwtAuthFilter).
     */
    @GetMapping("/me")
    public ResponseEntity<UsuarioDTO> me() {
        return ResponseEntity.ok(authService.me());
    }

    /**
     * Pede o link de redefinição de senha. Endpoint público.
     * <p>
     * Responde 204 SEMPRE — email cadastrado ou não. Distinguir os casos
     * transformaria o endpoint num oráculo de quem é cliente do sistema.
     * O 429 do rate limit é o único desvio, e vale igual para os dois casos.
     */
    @PostMapping("/senha/esqueci")
    public ResponseEntity<Void> esqueciSenha(@Valid @RequestBody SenhaResetDTO.Solicitacao req,
                                             HttpServletRequest request) {
        senhaResetService.solicitar(req.email(), request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    /**
     * Redefine a senha com o token recebido por email. Endpoint público.
     * Token inválido, expirado ou já usado retornam a MESMA mensagem.
     */
    @PostMapping("/senha/redefinir")
    public ResponseEntity<Void> redefinirSenha(@Valid @RequestBody SenhaResetDTO.Redefinicao req) {
        senhaResetService.redefinir(req.token(), req.novaSenha());
        return ResponseEntity.noContent().build();
    }

    /**
     * Cadastro público. Endpoint público de ESCRITA — o primeiro do sistema.
     * <p>
     * Responde 204 SEMPRE quando o pedido é bem-formado, exista o email ou não:
     * distinguir os casos transformaria o cadastro num oráculo de quem é cliente.
     * O que muda é qual email a pessoa recebe — link de confirmação, ou aviso de
     * que ela já tem conta.
     * <p>
     * Nada é criado aqui. O tenant só nasce no clique do link.
     */
    @PostMapping("/registro")
    public ResponseEntity<Void> registrar(@Valid @RequestBody RegistroDTO.Solicitacao req,
                                          HttpServletRequest request) {
        registroService.solicitar(req, request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    /**
     * Confirma o email e provisiona a empresa, a loja, o usuário ADMIN e o
     * trial. Endpoint público — quem prova ser dono do email é o token.
     * <p>
     * Já devolve os cookies de sessão: o cliente cai no sistema logado, sem
     * digitar de novo a senha que acabou de criar.
     */
    @PostMapping("/registro/confirmar")
    public ResponseEntity<LoginResponse> confirmarRegistro(@Valid @RequestBody RegistroDTO.Confirmacao req,
                                                           HttpServletResponse response) {
        var result = registroService.confirmar(req.token());
        setAuthCookies(response, result);
        return ResponseEntity.ok(new LoginResponse(result.usuario()));
    }

    private void setAuthCookies(HttpServletResponse response, AuthLoginResult result) {
        var jwtCookie = ResponseCookie.from("jwt", result.accessToken())
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .path("/api")
            .maxAge(Duration.ofMillis(86400000))
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());

        var refreshCookie = ResponseCookie.from("refresh_token", result.refreshToken())
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .path("/api/auth/refresh")
            .maxAge(Duration.ofDays(7))
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private void clearAuthCookies(HttpServletResponse response) {
        var jwtCookie = ResponseCookie.from("jwt", "")
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .path("/api")
            .maxAge(0)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());

        var refreshCookie = ResponseCookie.from("refresh_token", "")
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Strict")
            .path("/api/auth/refresh")
            .maxAge(0)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private String extractRefreshToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if ("refresh_token".equals(cookie.getName())) return cookie.getValue();
            }
        }
        return null;
    }
}
