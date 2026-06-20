package com.agenda.domain.pix;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.domain.arquivo.ArquivoService;
import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.loja.Loja;
import com.agenda.domain.loja.LojaRepository;
import com.agenda.shared.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebProperties;
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
class PixServiceTest {

    @Mock private PagamentoPixRepository pixRepository;
    @Mock private LojaRepository lojaRepository;
    @Mock private ArquivoService arquivoService;
    @Mock private AuditoriaService auditoriaService;

    private PixService pixService;
    private Empresa empresa;
    private Loja loja;

    @BeforeEach
    void setUp() {
        pixService = new PixService(pixRepository, lojaRepository, arquivoService, auditoriaService);
        empresa = Empresa.builder().id(UUID.randomUUID()).build();
        loja = Loja.builder().id(UUID.randomUUID()).empresa(empresa).nome("Loja Teste").cor("#3B82F6").build();
        TenantContext.setEmpresaId(empresa.getId());
    }

    @Test
    void criar_DeveSalvarPix() {
        var req = new CriarPixRequest(loja.getId(), "Fornecedor X",
            new BigDecimal("150.00"), LocalDate.now().plusDays(30), "chave-pix", "CPF", null);

        when(lojaRepository.findById(loja.getId())).thenReturn(Optional.of(loja));
        when(pixRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = pixService.criar(req);

        assertNotNull(result);
        assertEquals("Fornecedor X", result.fornecedor());
        assertEquals(StatusPix.PENDENTE, result.status());
        verify(auditoriaService).registrar(eq("CRIAR"), eq("PIX"), any(), anyString());
    }

    @Test
    void mudarStatus_DeveAtualizarPagoEmQuandoPAGO() {
        var pix = PagamentoPix.builder()
            .id(UUID.randomUUID()).empresa(empresa).loja(loja)
            .fornecedor("Teste").valor(new BigDecimal("100"))
            .vencimento(LocalDate.now()).status(StatusPix.PENDENTE)
            .chavePix("chave").build();

        when(pixRepository.findById(pix.getId())).thenReturn(Optional.of(pix));
        when(pixRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = pixService.mudarStatus(pix.getId(), StatusPix.PAGO);

        assertEquals(StatusPix.PAGO, result.status());
        assertNotNull(result.pagoEm());
        verify(auditoriaService).registrar(eq("MUDAR_STATUS"), eq("PIX"), any(), anyString());
    }

    @Test
    void excluir_DeveRemoverPix() {
        var pix = PagamentoPix.builder()
            .id(UUID.randomUUID()).empresa(empresa).loja(loja)
            .fornecedor("Teste").valor(new BigDecimal("100"))
            .vencimento(LocalDate.now()).chavePix("chave").build();

        when(pixRepository.findById(pix.getId())).thenReturn(Optional.of(pix));

        pixService.excluir(pix.getId());

        verify(pixRepository).delete(pix);
        verify(auditoriaService).registrar(eq("EXCLUIR"), eq("PIX"), eq(pix.getId()), anyString());
    }

    @Test
    void editar_DeveAtualizarDados() {
        var pix = PagamentoPix.builder()
            .id(UUID.randomUUID()).empresa(empresa).loja(loja)
            .fornecedor("Antigo").valor(new BigDecimal("100"))
            .vencimento(LocalDate.now()).chavePix("chave-antiga").build();
        var req = new EditarPixRequest(loja.getId(), "Novo Fornecedor",
            new BigDecimal("200"), LocalDate.now().plusDays(10), "chave-nova", "CNPJ", null);

        when(pixRepository.findById(pix.getId())).thenReturn(Optional.of(pix));
        when(lojaRepository.findById(loja.getId())).thenReturn(Optional.of(loja));
        when(pixRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = pixService.editar(pix.getId(), req);

        assertEquals("Novo Fornecedor", result.fornecedor());
        assertEquals(0, new BigDecimal("200").compareTo(result.valor()));
        verify(auditoriaService).registrar(eq("EDITAR"), eq("PIX"), eq(pix.getId()), anyString());
    }

    @Test
    void listar_DeveFiltrarPorEmpresa() {
        var pageable = PageRequest.of(0, 10);
        when(pixRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        PagamentoPix.builder().id(UUID.randomUUID()).empresa(empresa).loja(loja)
                                .fornecedor("Teste").valor(new BigDecimal("100"))
                                .vencimento(LocalDate.now()).chavePix("chave-teste").build()
                ), pageable, 1));

        var result = pixService.listar(null, null, null, null, null, pageable);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}