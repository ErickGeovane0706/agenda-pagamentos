package com.agenda.domain.auth;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.domain.usuario.PerfilUsuario;
import com.agenda.domain.usuario.Usuario;
import com.agenda.domain.usuario.UsuarioRepository;
import com.agenda.security.JwtService;
import com.agenda.security.RateLimiterService;
import com.agenda.security.RefreshTokenService;
import com.agenda.security.TokenBlacklistService;
import com.agenda.shared.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private AuditoriaService auditoriaService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(usuarioRepository, jwtService, refreshTokenService, tokenBlacklistService, authenticationManager, auditoriaService, new RateLimiterService());
    }

    @Test
    void login_DeveRetornarTokenQuandoCredenciaisValidas() {
        var dto = new AuthDTO("joao@test.com", "Senha123");
        var empresa = com.agenda.domain.empresa.Empresa.builder().id(UUID.randomUUID()).build();
        var usuario = Usuario.builder()
            .id(UUID.randomUUID()).empresa(empresa).nome("João")
            .email("joao@test.com").senhaHash("hash").perfil(PerfilUsuario.ADMIN)
            .ativo(true).build();

        when(usuarioRepository.findByEmail("joao@test.com")).thenReturn(Optional.of(usuario));
        when(jwtService.generateToken(usuario)).thenReturn("token");

        when(refreshTokenService.create(usuario)).thenReturn(
            com.agenda.domain.auth.RefreshToken.builder().token("rt").build());

        var result = authService.login(dto);

        assertNotNull(result);
        assertEquals("token", result.accessToken());
        verify(authenticationManager).authenticate(any());
    }

    @Test
    void login_DeveAuditarFalhaQuandoCredenciaisInvalidas() {
        var dto = new AuthDTO("joao@test.com", "senha-erraada");
        doThrow(new RuntimeException("Bad credentials"))
            .when(authenticationManager).authenticate(any());

        assertThrows(RuntimeException.class, () -> authService.login(dto));
        verify(auditoriaService).registrar(eq("LOGIN_FALHA"), eq("USUARIO"), isNull(), contains("joao@test.com"));
    }

    @Test
    void login_DeveBloquearApos10FalhasNoMesmoEmail() {
        var dto = new AuthDTO("alvo@test.com", "errada");
        doThrow(new RuntimeException("Bad credentials"))
            .when(authenticationManager).authenticate(any());

        // 10 falhas esgotam o bucket do email (cada uma propaga o erro de auth)
        for (int i = 0; i < 10; i++) {
            assertThrows(RuntimeException.class, () -> authService.login(dto));
        }

        // 11ª tentativa é barrada pelo rate limit (429), antes de autenticar
        assertThrows(TooManyRequestsException.class, () -> authService.login(dto));
    }

    @Test
    void login_SucessoNaoConsomeRateLimit() {
        var dto = new AuthDTO("joao@test.com", "Senha123");
        var empresa = com.agenda.domain.empresa.Empresa.builder().id(UUID.randomUUID()).build();
        var usuario = Usuario.builder()
            .id(UUID.randomUUID()).empresa(empresa).nome("João")
            .email("joao@test.com").senhaHash("hash").perfil(PerfilUsuario.ADMIN)
            .ativo(true).build();

        when(usuarioRepository.findByEmail("joao@test.com")).thenReturn(Optional.of(usuario));
        when(jwtService.generateToken(usuario)).thenReturn("token");
        when(refreshTokenService.create(usuario)).thenReturn(
            com.agenda.domain.auth.RefreshToken.builder().token("rt").build());

        // Bem além do teto de falhas: como o token só é gasto em FALHA,
        // um usuário legítimo nunca é bloqueado.
        for (int i = 0; i < 15; i++) {
            assertDoesNotThrow(() -> authService.login(dto));
        }
    }

    @Test
    void login_DeveLancarExcecaoQuandoUsuarioInativo() {
        var dto = new AuthDTO("joao@test.com", "Senha123");
        var usuario = Usuario.builder()
            .id(UUID.randomUUID()).email("joao@test.com")
            .perfil(PerfilUsuario.ADMIN).ativo(false).build();

        when(usuarioRepository.findByEmail("joao@test.com")).thenReturn(Optional.of(usuario));

        assertThrows(RuntimeException.class, () -> authService.login(dto));
        verify(auditoriaService).registrar(eq("LOGIN_FALHA"), eq("USUARIO"), eq(usuario.getId()), contains("inativo"));
    }
}
