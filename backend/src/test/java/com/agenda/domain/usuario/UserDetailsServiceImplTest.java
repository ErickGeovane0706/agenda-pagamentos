package com.agenda.domain.usuario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock private UsuarioRepository usuarioRepository;
    private UserDetailsServiceImpl userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new UserDetailsServiceImpl(usuarioRepository);
    }

    @Test
    void loadUserByUsername_DeveRetornarUserDetails() {
        var empresa = com.agenda.domain.empresa.Empresa.builder().id(UUID.randomUUID()).build();
        var usuario = Usuario.builder()
            .id(UUID.randomUUID()).empresa(empresa).nome("João")
            .email("joao@test.com").senhaHash("hash")
            .perfil(PerfilUsuario.ADMIN).ativo(true).build();

        when(usuarioRepository.findByEmail("joao@test.com")).thenReturn(Optional.of(usuario));

        var result = userDetailsService.loadUserByUsername("joao@test.com");

        assertEquals("joao@test.com", result.getUsername());
        assertEquals("hash", result.getPassword());
        assertTrue(result.isEnabled());
        assertTrue(result.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void loadUserByUsername_DeveLancarExcecaoQuandoNaoEncontrado() {
        when(usuarioRepository.findByEmail("inexistente@test.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
            () -> userDetailsService.loadUserByUsername("inexistente@test.com"));
    }
}