package com.agenda.security;

import com.agenda.config.JwtConfig;
import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.usuario.PerfilUsuario;
import com.agenda.domain.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock private TokenBlacklistService tokenBlacklistService;

    private JwtService jwtService;
    private JwtConfig jwtConfig;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        jwtConfig = new JwtConfig();
        jwtConfig.setSecret("dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLXRlc3RpbmctcHVycG9zZXMtYW5kLWl0LWlzLWxvbmctZW5vdWdo");
        jwtConfig.setExpiration(86400000L);
        jwtService = new JwtService(jwtConfig, tokenBlacklistService);
        var empresa = Empresa.builder().id(UUID.randomUUID()).build();
        usuario = Usuario.builder()
            .id(UUID.randomUUID()).empresa(empresa)
            .nome("João").email("joao@test.com")
            .senhaHash("hash").perfil(PerfilUsuario.ADMIN).build();
    }

    @Test
    void generateToken_DeveCriarTokenValido() {
        var token = jwtService.generateToken(usuario);
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void extractEmail_DeveRetornarEmail() {
        var token = jwtService.generateToken(usuario);
        assertEquals("joao@test.com", jwtService.extractEmail(token));
    }

    @Test
    void extractEmpresaId_DeveRetornarId() {
        var token = jwtService.generateToken(usuario);
        assertEquals(usuario.getEmpresa().getId(), jwtService.extractEmpresaId(token));
    }

    @Test
    void extractPerfil_DeveRetornarPerfil() {
        var token = jwtService.generateToken(usuario);
        assertEquals("ADMIN", jwtService.extractPerfil(token));
    }

    @Test
    void extractNome_DeveRetornarNome() {
        var token = jwtService.generateToken(usuario);
        assertEquals("João", jwtService.extractNome(token));
    }

    @Test
    void isValid_DeveRetornarTrueParaTokenValido() {
        var token = jwtService.generateToken(usuario);
        var userDetails = new User("joao@test.com", "hash", true, true, true, true, List.of());
        assertTrue(jwtService.isValid(token, userDetails));
    }

    @Test
    void isValid_DeveRetornarFalseParaEmailDiferente() {
        var token = jwtService.generateToken(usuario);
        var userDetails = new User("outro@test.com", "hash", true, true, true, true, List.of());
        assertFalse(jwtService.isValid(token, userDetails));
    }
}