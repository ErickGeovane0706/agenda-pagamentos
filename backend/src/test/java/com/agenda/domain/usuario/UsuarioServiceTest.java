package com.agenda.domain.usuario;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.empresa.EmpresaRepository;
import com.agenda.shared.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordValidator passwordValidator;
    @Mock private AuditoriaService auditoriaService;

    private UsuarioService usuarioService;
    private UUID empresaId;
    private Empresa empresa;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuarioService = new UsuarioService(usuarioRepository, empresaRepository,
            passwordEncoder, passwordValidator, auditoriaService);
        empresaId = UUID.randomUUID();
        empresa = Empresa.builder().id(empresaId).nome("Empresa Teste").build();
        usuario = Usuario.builder()
            .id(UUID.randomUUID()).empresa(empresa).nome("João")
            .email("joao@test.com").senhaHash("hash").perfil(PerfilUsuario.ADMIN)
            .build();
        TenantContext.setEmpresaId(empresaId);
    }

    @Test
    void criar_DeveSalvarUsuario() {
        when(empresaRepository.getReferenceById(empresaId)).thenReturn(empresa);
        when(passwordEncoder.encode("Senha123")).thenReturn("hash");
        when(usuarioRepository.save(any())).thenAnswer(i -> {
            var u = i.getArgument(0, Usuario.class);
            u.setId(UUID.randomUUID());
            return u;
        });

        var req = new CriarUsuarioRequest("Novo Usuário", "novo@test.com", "Senha123", PerfilUsuario.OPERADOR);
        var result = usuarioService.criar(req);

        assertEquals("Novo Usuário", result.nome());
        assertEquals(PerfilUsuario.OPERADOR, result.perfil());
        verify(passwordValidator).validar("Senha123");
        verify(auditoriaService).registrar(eq("CRIAR"), eq("USUARIO"), any(), anyString());
    }

    @Test
    void editar_DeveAtualizarDados() {
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var req = new EditarUsuarioRequest("João Editado", "joao@editado.com", null);
        var result = usuarioService.editar(usuario.getId(), req);

        assertEquals("João Editado", result.nome());
        assertEquals("joao@editado.com", result.email());
        verify(passwordValidator, never()).validar(any());
        verify(auditoriaService).registrar(eq("EDITAR"), eq("USUARIO"), eq(usuario.getId()), any());
    }

    @Test
    void editar_DeveAtualizarSenhaQuandoInformada() {
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(passwordEncoder.encode("NovaSenha123")).thenReturn("novoHash");

        var req = new EditarUsuarioRequest("João", "joao@test.com", "NovaSenha123");
        usuarioService.editar(usuario.getId(), req);

        verify(passwordValidator).validar("NovaSenha123");
        verify(passwordEncoder).encode("NovaSenha123");
    }

    @Test
    void editar_DeveLancarExcecaoQuandoNaoEncontrado() {
        when(usuarioRepository.findById(any())).thenReturn(Optional.empty());
        var req = new EditarUsuarioRequest("João", "joao@test.com", null);
        assertThrows(RuntimeException.class, () -> usuarioService.editar(UUID.randomUUID(), req));
    }

    @Test
    void editar_DeveLancarExcecaoQuandoAcessoNegado() {
        var outraEmpresa = Empresa.builder().id(UUID.randomUUID()).build();
        var usuarioOutraEmpresa = Usuario.builder()
            .id(UUID.randomUUID()).empresa(outraEmpresa).build();
        when(usuarioRepository.findById(usuarioOutraEmpresa.getId())).thenReturn(Optional.of(usuarioOutraEmpresa));

        var req = new EditarUsuarioRequest("João", "joao@test.com", null);
        assertThrows(RuntimeException.class, () -> usuarioService.editar(usuarioOutraEmpresa.getId(), req));
    }

    @Test
    void excluir_DeveRemoverUsuario() {
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        usuarioService.excluir(usuario.getId());
        verify(usuarioRepository).delete(usuario);
        verify(auditoriaService).registrar(eq("EXCLUIR"), eq("USUARIO"), eq(usuario.getId()), any());
    }

    @Test
    void excluir_DeveLancarExcecaoQuandoAcessoNegado() {
        var outraEmpresa = Empresa.builder().id(UUID.randomUUID()).build();
        var usuarioOutraEmpresa = Usuario.builder()
            .id(UUID.randomUUID()).empresa(outraEmpresa).build();
        when(usuarioRepository.findById(usuarioOutraEmpresa.getId())).thenReturn(Optional.of(usuarioOutraEmpresa));
        assertThrows(RuntimeException.class, () -> usuarioService.excluir(usuarioOutraEmpresa.getId()));
    }

    @Test
    void solicitarExclusao_DeveMarcarEmpresa() {
        when(empresaRepository.findById(empresaId)).thenReturn(Optional.of(empresa));
        when(empresaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        usuarioService.solicitarExclusao();

        assertTrue(empresa.getSolicitouExclusao());
        assertNotNull(empresa.getSolicitouExclusaoEm());
        verify(auditoriaService).registrar(eq("SOLICITAR_EXCLUSAO"), eq("EMPRESA"), eq(empresaId), any());
    }
}