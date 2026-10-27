package com.agenda.domain.banco;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.empresa.EmpresaRepository;
import com.agenda.shared.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BancoServiceTest {

    @Mock private BancoRepository bancoRepository;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private AuditoriaService auditoriaService;

    private BancoService bancoService;
    private Empresa empresa;

    @BeforeEach
    void setUp() {
        bancoService = new BancoService(bancoRepository, empresaRepository, auditoriaService);
        empresa = Empresa.builder().id(UUID.randomUUID()).nome("Empresa Teste").build();
        TenantContext.setEmpresaId(empresa.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listar_DeveRetornarBancosDaEmpresa() {
        var banco = Banco.builder().id(UUID.randomUUID()).empresa(empresa).nome("Banco do Brasil").build();
        when(bancoRepository.findByEmpresaIdOrderByNome(empresa.getId()))
            .thenReturn(List.of(banco));

        var result = bancoService.listar();

        assertEquals(1, result.size());
        assertEquals("Banco do Brasil", result.getFirst().nome());
    }

    @Test
    void criar_DeveSalvarBanco() {
        var req = new CriarBancoRequest("Itaú", "341", "0123", "45678-X");
        when(empresaRepository.getReferenceById(empresa.getId())).thenReturn(empresa);
        when(bancoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = bancoService.criar(req);

        assertEquals("Itaú", result.nome());
        assertEquals("341", result.codigo());
        assertEquals("0123", result.agencia());
        assertEquals("45678-X", result.conta());
        verify(auditoriaService).registrar(eq("CRIAR"), eq("BANCO"), any(), anyString());
    }

    @Test
    void criar_SemAgenciaEConta_DeveFuncionarComoAntes() {
        when(empresaRepository.getReferenceById(empresa.getId())).thenReturn(empresa);
        when(bancoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = bancoService.criar(new CriarBancoRequest("Caixa", null, null, null));

        assertNull(result.agencia());
        assertNull(result.conta());
    }

    @Test
    void editar_DeveAtualizarDados() {
        var banco = Banco.builder().id(UUID.randomUUID()).empresa(empresa).nome("Itaú").codigo("341").build();
        var req = new EditarBancoRequest("Itaú Editado", "341", "0123", "45678-9");

        when(bancoRepository.findById(banco.getId())).thenReturn(Optional.of(banco));
        when(bancoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = bancoService.editar(banco.getId(), req);

        assertEquals("Itaú Editado", result.nome());
        assertEquals("0123", result.agencia());
        assertEquals("45678-9", result.conta());
        verify(auditoriaService).registrar(eq("EDITAR"), eq("BANCO"), eq(banco.getId()), anyString());
    }

    @Test
    void editar_CampoEmBrancoApagaAgenciaEConta() {
        // O formulário manda "" quando o campo fica vazio; guardar "" faria a
        // tela mostrar "Ag  · Conta" em vez de esconder.
        var banco = Banco.builder().id(UUID.randomUUID()).empresa(empresa).nome("Itaú")
            .agencia("0123").conta("45678-9").build();
        when(bancoRepository.findById(banco.getId())).thenReturn(Optional.of(banco));
        when(bancoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = bancoService.editar(banco.getId(), new EditarBancoRequest("Itaú", null, "  ", ""));

        assertNull(result.agencia());
        assertNull(result.conta());
    }

    @Test
    void excluir_DeveRemoverBanco() {
        var banco = Banco.builder().id(UUID.randomUUID()).empresa(empresa).nome("Itaú").build();
        when(bancoRepository.findById(banco.getId())).thenReturn(Optional.of(banco));

        bancoService.excluir(banco.getId());

        verify(bancoRepository).delete(banco);
        verify(auditoriaService).registrar(eq("EXCLUIR"), eq("BANCO"), eq(banco.getId()), anyString());
    }
}