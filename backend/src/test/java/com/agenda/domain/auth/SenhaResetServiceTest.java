package com.agenda.domain.auth;

import com.agenda.domain.usuario.PasswordValidator;
import com.agenda.domain.usuario.Usuario;
import com.agenda.domain.usuario.UsuarioRepository;
import com.agenda.email.EmailService;
import com.agenda.security.RateLimiterService;
import com.agenda.security.RefreshTokenService;
import com.agenda.shared.exception.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Cobre o "critério de pronto" de segurança do PLANO-REGISTRO.md para o
 * fluxo de redefinição: anti-enumeração, token de uso único, rate limit
 * nas duas dimensões e revogação de sessões.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SenhaResetServiceTest {

    @Mock private SenhaResetTokenRepository tokenRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private EmailService emailService;

    private SenhaResetService service;

    @BeforeEach
    void setUp() {
        service = new SenhaResetService(tokenRepository, usuarioRepository, new PasswordValidator(),
                passwordEncoder, refreshTokenService, new RateLimiterService(), emailService);
        ReflectionTestUtils.setField(service, "ttlHoras", 2);
        ReflectionTestUtils.setField(service, "frontendUrl", "https://app.teste");
    }

    private Usuario usuario() {
        var u = new Usuario();
        u.setId(UUID.randomUUID());
        u.setNome("Fulano");
        u.setEmail("fulano@test.com");
        u.setSenhaHash("hash-antigo");
        return u;
    }

    // ─── Anti-enumeração ──────────────────────────────────────────────────────

    @Test
    void solicitar_EmailInexistente_NaoGeraTokenNemEmailENaoLanca() {
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.solicitar("naoexiste@test.com", "10.0.0.1"));

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).enviar(any(), any(), any());
    }

    @Test
    void solicitar_EmailExistente_GeraTokenEEnviaEmail() {
        var u = usuario();
        when(usuarioRepository.findByEmail("fulano@test.com")).thenReturn(Optional.of(u));

        service.solicitar("fulano@test.com", "10.0.0.1");

        verify(tokenRepository).save(any(SenhaResetToken.class));
        verify(emailService).enviar(eq("fulano@test.com"), anyString(), anyString());
    }

    @Test
    void solicitar_NormalizaEmailComEspacoEMaiuscula() {
        when(usuarioRepository.findByEmail("fulano@test.com")).thenReturn(Optional.of(usuario()));

        service.solicitar("  Fulano@Test.COM  ", "10.0.0.1");

        verify(usuarioRepository).findByEmail("fulano@test.com");
    }

    // ─── O banco nunca vê o token cru ─────────────────────────────────────────

    @Test
    void solicitar_GravaApenasHash_NuncaOTokenDoLink() {
        var u = usuario();
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.of(u));

        service.solicitar("fulano@test.com", "10.0.0.1");

        var tokenSalvo = ArgumentCaptor.forClass(SenhaResetToken.class);
        var corpoEmail = ArgumentCaptor.forClass(String.class);
        verify(tokenRepository).save(tokenSalvo.capture());
        verify(emailService).enviar(anyString(), anyString(), corpoEmail.capture());

        String hash = tokenSalvo.getValue().getTokenHash();
        assertEquals(64, hash.length(), "SHA-256 em hex tem 64 caracteres");
        assertFalse(corpoEmail.getValue().contains(hash), "o hash do banco não pode vazar no email");
        assertTrue(corpoEmail.getValue().contains("https://app.teste/redefinir-senha?token="));
    }

    // ─── Rate limit nas duas dimensões ────────────────────────────────────────

    @Test
    void solicitar_EstouraTetoPorIp_Lanca429() {
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // Mesmo IP, emails diferentes: só o teto por IP pode disparar.
        for (int i = 0; i < 5; i++) {
            service.solicitar("email" + i + "@test.com", "10.0.0.9");
        }

        assertThrows(TooManyRequestsException.class,
                () -> service.solicitar("email99@test.com", "10.0.0.9"));
    }

    @Test
    void solicitar_EstouraTetoPorEmail_Lanca429() {
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // IPs diferentes, mesmo email: só o teto por email pode disparar.
        for (int i = 0; i < 3; i++) {
            service.solicitar("alvo@test.com", "10.0.1." + i);
        }

        assertThrows(TooManyRequestsException.class,
                () -> service.solicitar("alvo@test.com", "10.0.1.99"));
    }

    // ─── Token: inválido, expirado e já usado dão a MESMA resposta ────────────

    @Test
    void redefinir_TokenInexistente_ErroGenerico() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        var ex = assertThrows(IllegalArgumentException.class,
                () -> service.redefinir("token-qualquer", "NovaSenha123"));
        assertEquals("Link inválido ou expirado.", ex.getMessage());
    }

    @Test
    void redefinir_TokenExpirado_MesmoErroGenerico() {
        var token = SenhaResetToken.builder()
                .usuarioId(UUID.randomUUID())
                .tokenHash("x")
                .expiraEm(LocalDateTime.now().minusMinutes(1))
                .build();
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        var ex = assertThrows(IllegalArgumentException.class,
                () -> service.redefinir("token-qualquer", "NovaSenha123"));
        assertEquals("Link inválido ou expirado.", ex.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void redefinir_TokenJaUsado_MesmoErroGenerico() {
        var token = SenhaResetToken.builder()
                .usuarioId(UUID.randomUUID())
                .tokenHash("x")
                .expiraEm(LocalDateTime.now().plusHours(1))
                .usadoEm(LocalDateTime.now().minusMinutes(5))
                .build();
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        var ex = assertThrows(IllegalArgumentException.class,
                () -> service.redefinir("token-qualquer", "NovaSenha123"));
        assertEquals("Link inválido ou expirado.", ex.getMessage());
        verify(usuarioRepository, never()).save(any());
    }

    // ─── Caminho feliz ────────────────────────────────────────────────────────

    @Test
    void redefinir_TokenValido_TrocaSenhaMarcaUsadoERevogaSessoes() {
        var u = usuario();
        var token = SenhaResetToken.builder()
                .usuarioId(u.getId())
                .tokenHash("x")
                .expiraEm(LocalDateTime.now().plusHours(1))
                .build();
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(usuarioRepository.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.encode("NovaSenha123")).thenReturn("hash-novo");

        service.redefinir("token-cru", "NovaSenha123");

        assertEquals("hash-novo", u.getSenhaHash());
        assertNotNull(token.getUsadoEm(), "token deve ficar marcado como usado");
        verify(usuarioRepository).save(u);
        verify(refreshTokenService).revokeAll(u.getId());
    }

    @Test
    void redefinir_SenhaFraca_RejeitaESemTrocar() {
        var u = usuario();
        var token = SenhaResetToken.builder()
                .usuarioId(u.getId())
                .tokenHash("x")
                .expiraEm(LocalDateTime.now().plusHours(1))
                .build();
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(usuarioRepository.findById(u.getId())).thenReturn(Optional.of(u));

        assertThrows(IllegalArgumentException.class, () -> service.redefinir("token-cru", "123"));

        verify(usuarioRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAll(any());
        assertNull(token.getUsadoEm(), "token não pode ser consumido se a senha foi rejeitada");
    }
}
