package com.agenda.job;

import com.agenda.domain.boleto.BoletoRepository;
import com.agenda.domain.cheque.ChequeRepository;
import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.empresa.EmpresaRepository;
import com.agenda.domain.pix.PagamentoPixRepository;
import com.agenda.domain.usuario.Usuario;
import com.agenda.domain.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private BoletoRepository boletoRepository;
    @Mock private PagamentoPixRepository pagamentoPixRepository;
    @Mock private ChequeRepository chequeRepository;

    private SchedulerService schedulerService;
    private UUID empresaId;
    private Empresa empresa;

    @BeforeEach
    void setUp() {
        schedulerService = new SchedulerService(usuarioRepository, empresaRepository, boletoRepository, pagamentoPixRepository, chequeRepository);
        empresaId = UUID.randomUUID();
        empresa = Empresa.builder().id(empresaId).nome("Empresa Teste").build();
    }

    @Test
    void processarExclusoes_DeveAnonimizarEmpresaEUsuarios() {
        var usuario = Usuario.builder()
            .id(UUID.randomUUID()).empresa(empresa).nome("João")
            .email("joao@test.com").senhaHash("hash")
            .build();

        when(empresaRepository.findBySolicitouExclusaoTrueAndExcluidoEmIsNull())
            .thenReturn(List.of(empresa));
        when(usuarioRepository.findByEmpresaId(empresaId)).thenReturn(List.of(usuario));
        when(boletoRepository.findByEmpresa_Id(empresaId)).thenReturn(List.of());
        when(pagamentoPixRepository.findByEmpresa_Id(empresaId)).thenReturn(List.of());
        when(chequeRepository.findByEmpresa_Id(empresaId)).thenReturn(List.of());

        schedulerService.processarExclusoes();

        assertEquals("Usuário Removido", usuario.getNome());
        assertTrue(usuario.getEmail().startsWith("removido_"));
        assertEquals("REMOVIDO", usuario.getSenhaHash());
        assertNotNull(usuario.getExcluidoEm());
        assertNotNull(empresa.getExcluidoEm());

        verify(usuarioRepository).save(usuario);
        verify(empresaRepository).save(empresa);
    }

    @Test
    void processarExclusoes_DeveIgnorarQuandoNaoHaPendentes() {
        when(empresaRepository.findBySolicitouExclusaoTrueAndExcluidoEmIsNull())
            .thenReturn(List.of());

        schedulerService.processarExclusoes();

        verify(usuarioRepository, never()).findByEmpresaId(any());
        verify(empresaRepository, never()).save(any());
    }
}
