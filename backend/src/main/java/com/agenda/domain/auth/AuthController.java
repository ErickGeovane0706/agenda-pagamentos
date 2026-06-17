package com.agenda.domain.auth;

import com.agenda.domain.usuario.UsuarioDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid AuthDTO dto,
                                                HttpServletResponse response) {
        var result = authService.login(dto);
        setAuthCookies(response, result);
        return ResponseEntity.ok(new LoginResponse(result.usuario()));
    }

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

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request,
                                        HttpServletResponse response) {
        authService.logout(request);
        clearAuthCookies(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UsuarioDTO> me() {
        return ResponseEntity.ok(authService.me());
    }

    private void setAuthCookies(HttpServletResponse response, AuthLoginResult result) {
        var jwtCookie = ResponseCookie.from("jwt", result.accessToken())
            .httpOnly(true)
            .secure(false)
            .sameSite("Strict")
            .path("/api")
            .maxAge(Duration.ofMillis(86400000))
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());

        var refreshCookie = ResponseCookie.from("refresh_token", result.refreshToken())
            .httpOnly(true)
            .secure(false)
            .sameSite("Strict")
            .path("/api/auth/refresh")
            .maxAge(Duration.ofDays(7))
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private void clearAuthCookies(HttpServletResponse response) {
        var jwtCookie = ResponseCookie.from("jwt", "")
            .httpOnly(true)
            .secure(false)
            .sameSite("Strict")
            .path("/api")
            .maxAge(0)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie.toString());

        var refreshCookie = ResponseCookie.from("refresh_token", "")
            .httpOnly(true)
            .secure(false)
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
