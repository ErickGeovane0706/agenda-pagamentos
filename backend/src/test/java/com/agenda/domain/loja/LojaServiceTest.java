package com.agenda.domain.loja;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.domain.assinatura.AssinaturaService;
import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.empresa.EmpresaRepository;
import com.agenda.shared.TenantContext;
import com.agenda.shared.exception.PagamentoRequeridoException;
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
class LojaServiceTest {

    @Mock private LojaRepository lojaRepository;
    @Mock private EmpresaRepository empresaRepository;
    @Mock private AuditoriaService auditoriaService;
    @Mock private AssinaturaService assinaturaService;

    private LojaService lojaService;
    private Empresa empresa;

    @BeforeEach
    void setUp() {
        lojaService = new LojaService(lojaRepository, empresaRepository, auditoriaService, assinaturaService);
        empresa = Empresa.builder().id(UUID.randomUUID()).nome("Empresa Teste").build();
        TenantContext.setEmpresaId(empresa.getId());
    }

    @Test
    void listar_DeveRetornarApenasLojasDaEmpresa() {
        var loja = Loja.builder().id(UUID.randomUUID()).empresa(empresa).nome("Loja A").cor("#1e40af").build();
        when(lojaRepository.findByEmpresaIdOrderByNome(empresa.getId()))
            .thenReturn(List.of(loja));

        var result = lojaService.listar();

        assertEquals(1, result.size());
        assertEquals("Loja A", result.getFirst().nome());
    }

    @Test
    void criar_DeveSalvarLoja() {
        var req = new CriarLojaRequest("Loja Nova", null, null, "#065f46");
        when(lojaRepository.countByEmpresaId(empresa.getId())).thenReturn(0L);
        when(assinaturaService.podeCriarLoja(empresa.getId(), 0L)).thenReturn(true);
        when(empresaRepository.getReferenceById(empresa.getId())).thenReturn(empresa);
        when(lojaRepository.save(any())).thenAnswer(i -> {
            var l = i.getArgument(0, Loja.class);
            return Loja.builder().id(UUID.randomUUID()).empresa(l.getEmpresa())
                .nome(l.getNome()).cor(l.getCor()).build();
        });

        var result = lojaService.criar(req);

        assertNotNull(result);
        assertEquals("Loja Nova", result.nome());
        assertEquals("#065f46", result.cor());
        verify(auditoriaService).registrar(eq("CRIAR"), eq("LOJA"), any(), anyString());
    }

    @Test
    void criar_DeveRetornar402QuandoAssinaturaNaoCobreMaisLojas() {
        var req = new CriarLojaRequest("Loja Extra", null, null, "#065f46");
        when(lojaRepository.countByEmpresaId(empresa.getId())).thenReturn(1L);
        when(assinaturaService.podeCriarLoja(empresa.getId(), 1L)).thenReturn(false);

        assertThrows(PagamentoRequeridoException.class, () -> lojaService.criar(req));
        verify(lojaRepository, never()).save(any());
    }

    @Test
    void excluir_DeveDeletarLojaDaEmpresa() {
        var loja = Loja.builder().id(UUID.randomUUID()).empresa(empresa).nome("Loja X").cor("#b91c1c").build();
        when(lojaRepository.findById(loja.getId())).thenReturn(Optional.of(loja));

        lojaService.excluir(loja.getId());

        verify(lojaRepository).delete(loja);
        verify(auditoriaService).registrar(eq("EXCLUIR"), eq("LOJA"), eq(loja.getId()), anyString());
    }

    @Test
    void editar_DeveAtualizarDados() {
        var loja = Loja.builder().id(UUID.randomUUID()).empresa(empresa).nome("Loja Antiga").cor("#1e40af").build();
        var req = new EditarLojaRequest("Loja Editada", "12.345.678/0001-99", "Nova descrição", "#b91c1c");

        when(lojaRepository.findById(loja.getId())).thenReturn(Optional.of(loja));
        when(lojaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = lojaService.editar(loja.getId(), req);

        assertEquals("Loja Editada", result.nome());
        assertEquals("#b91c1c", result.cor());
        verify(auditoriaService).registrar(eq("EDITAR"), eq("LOJA"), eq(loja.getId()), anyString());
    }

    @Test
    void buscar_DeveRetornarLoja() {
        var loja = Loja.builder().id(UUID.randomUUID()).empresa(empresa).nome("Loja X").cor("#1e40af").build();
        when(lojaRepository.findById(loja.getId())).thenReturn(Optional.of(loja));

        var result = lojaService.buscar(loja.getId());

        assertEquals("Loja X", result.nome());
    }

    @Test
    void excluir_DeveLancarExcecaoQuandoLojaNaoPertenceEmpresa() {
        var outraEmpresa = Empresa.builder().id(UUID.randomUUID()).build();
        var loja = Loja.builder().id(UUID.randomUUID()).empresa(outraEmpresa).build();

        when(lojaRepository.findById(loja.getId())).thenReturn(Optional.of(loja));

        assertThrows(RuntimeException.class, () -> lojaService.excluir(loja.getId()));
        verify(lojaRepository, never()).delete(any());
    }
}
