package com.agenda.domain.boleto;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.loja.Loja;
import com.agenda.domain.loja.LojaRepository;
import com.agenda.shared.TenantContext;
import com.agenda.shared.exception.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoletoServiceTest {

    @Mock private BoletoRepository boletoRepository;
    @Mock private LojaRepository lojaRepository;
    @Mock private AuditoriaService auditoriaService;

    private BoletoService boletoService;

    private Empresa empresa;
    private Loja loja;

    @BeforeEach
    void setUp() {
        var arquivoService = mock(com.agenda.domain.arquivo.ArquivoService.class);
        boletoService = new BoletoService(boletoRepository, lojaRepository, arquivoService, auditoriaService);
        empresa = Empresa.builder().id(UUID.randomUUID()).build();
        loja = Loja.builder().id(UUID.randomUUID()).empresa(empresa).nome("Loja Teste").cor("#3B82F6").build();
        TenantContext.setEmpresaId(empresa.getId());
    }

    @Test
    void criar_DeveSalvarBoleto() {
        var req = new CriarBoletoRequest(loja.getId(), "Fornecedor X",
            new BigDecimal("150.00"), LocalDate.now().plusDays(30), null, null);

        when(lojaRepository.findById(loja.getId())).thenReturn(Optional.of(loja));
        when(boletoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = boletoService.criar(req);

        assertNotNull(result);
        assertEquals("Fornecedor X", result.fornecedor());
        assertEquals(0, new BigDecimal("150.00").compareTo(result.valor()));
        assertEquals(StatusBoleto.PENDENTE, result.status());
        verify(auditoriaService).registrar(eq("CRIAR"), eq("BOLETO"), any(), anyString());
    }

    @Test
    void criar_DeveLancarExcecaoQuandoLojaNaoPertenceEmpresa() {
        var outraEmpresa = Empresa.builder().id(UUID.randomUUID()).build();
        var lojaOutra = Loja.builder().id(UUID.randomUUID()).empresa(outraEmpresa).build();
        var req = new CriarBoletoRequest(lojaOutra.getId(), "Fornecedor X",
            new BigDecimal("100"), LocalDate.now(), null, null);

        when(lojaRepository.findById(lojaOutra.getId())).thenReturn(Optional.of(lojaOutra));

        assertThrows(AccessDeniedException.class, () -> boletoService.criar(req));
    }

    @Test
    void mudarStatus_DeveAtualizarPagoEmQuandoPAGO() {
        var boleto = Boleto.builder()
            .id(UUID.randomUUID()).empresa(empresa).loja(loja)
            .fornecedor("Teste").valor(new BigDecimal("100"))
            .vencimento(LocalDate.now()).status(StatusBoleto.PENDENTE)
            .build();

        when(boletoRepository.findById(boleto.getId())).thenReturn(Optional.of(boleto));
        when(boletoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = boletoService.mudarStatus(boleto.getId(), StatusBoleto.PAGO);

        assertEquals(StatusBoleto.PAGO, result.status());
        assertNotNull(result.pagoEm());
    }

    @Test
    void editar_DeveAtualizarDados() {
        var boleto = Boleto.builder()
            .id(UUID.randomUUID()).empresa(empresa).loja(loja)
            .fornecedor("Antigo").valor(new BigDecimal("100"))
            .vencimento(LocalDate.now()).build();
        var req = new EditarBoletoRequest(loja.getId(), "Novo Fornecedor",
            new BigDecimal("200"), LocalDate.now().plusDays(10), "12345678901", null);

        when(boletoRepository.findById(boleto.getId())).thenReturn(Optional.of(boleto));
        when(lojaRepository.findById(loja.getId())).thenReturn(Optional.of(loja));
        when(boletoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = boletoService.editar(boleto.getId(), req);

        assertEquals("Novo Fornecedor", result.fornecedor());
        assertEquals(0, new BigDecimal("200").compareTo(result.valor()));
        verify(auditoriaService).registrar(eq("EDITAR"), eq("BOLETO"), eq(boleto.getId()), anyString());
    }

    @Test
    void excluir_DeveRemoverBoleto() {
        var boleto = Boleto.builder()
            .id(UUID.randomUUID()).empresa(empresa).loja(loja)
            .fornecedor("Teste").valor(new BigDecimal("100"))
            .vencimento(LocalDate.now()).build();

        when(boletoRepository.findById(boleto.getId())).thenReturn(Optional.of(boleto));

        boletoService.excluir(boleto.getId());

        verify(boletoRepository).delete(boleto);
        verify(auditoriaService).registrar(eq("EXCLUIR"), eq("BOLETO"), eq(boleto.getId()), anyString());
    }

    @Test
    void listar_DeveFiltrarPorEmpresa() {
        var pageable = PageRequest.of(0, 10);
        when(boletoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        Boleto.builder().id(UUID.randomUUID()).empresa(empresa).loja(loja).fornecedor("Teste").valor(new BigDecimal("100")).vencimento(LocalDate.now()).build()
                ), pageable, 1));

        var result = boletoService.listar(null, null, null, null, null, pageable);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
