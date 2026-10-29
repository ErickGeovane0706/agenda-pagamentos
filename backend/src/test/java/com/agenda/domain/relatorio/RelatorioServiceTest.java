package com.agenda.domain.relatorio;

import com.agenda.domain.boleto.BoletoRepository;
import com.agenda.domain.cheque.ChequeRepository;
import com.agenda.domain.pix.PagamentoPixRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelatorioServiceTest {

    @Mock private BoletoRepository boletoRepository;
    @Mock private PagamentoPixRepository pagamentoPixRepository;
    @Mock private ChequeRepository chequeRepository;

    private RelatorioService relatorioService;
    private UUID empresaId;
    private LocalDate de;
    private LocalDate ate;

    @BeforeEach
    void setUp() {
        relatorioService = new RelatorioService(boletoRepository, pagamentoPixRepository, chequeRepository);
        empresaId = UUID.randomUUID();
        de = LocalDate.of(2026, 1, 1);
        ate = LocalDate.of(2026, 12, 31);
    }

    @Test
    void gerar_DeveRetornarResumoCompletoQuandoTipoTodos() {
        var boletosRows = new ArrayList<Object[]>();
        boletosRows.add(new Object[]{"PAGO", 2L, BigDecimal.valueOf(1000)});
        boletosRows.add(new Object[]{"PENDENTE", 1L, BigDecimal.valueOf(500)});
        when(boletoRepository.resumo(any(), any(), any(), any())).thenReturn(boletosRows);

        var pixRows = new ArrayList<Object[]>();
        pixRows.add(new Object[]{"PAGO", 1L, BigDecimal.valueOf(300)});
        when(pagamentoPixRepository.resumo(any(), any(), any(), any())).thenReturn(pixRows);

        var chequeRows = new ArrayList<Object[]>();
        chequeRows.add(new Object[]{"COMPENSADO", 1L, BigDecimal.valueOf(200)});
        chequeRows.add(new Object[]{"PENDENTE", 1L, BigDecimal.valueOf(100)});
        when(chequeRepository.resumo(any(), any(), any(), any())).thenReturn(chequeRows);

        var result = relatorioService.gerar(empresaId, de, ate, null, "TODOS", null);

        assertEquals(BigDecimal.valueOf(2100), result.get("totalGeral"));

        @SuppressWarnings("unchecked")
        var boletos = (java.util.Map<String, Object>) result.get("boletos");
        assertEquals(3L, boletos.get("total"));
        assertEquals(BigDecimal.valueOf(1500), boletos.get("valor"));
        assertEquals(2L, boletos.get("pago"));
        assertEquals(1L, boletos.get("pendente"));

        @SuppressWarnings("unchecked")
        var cheques = (java.util.Map<String, Object>) result.get("cheques");
        assertEquals(2L, cheques.get("total"));
        assertEquals(1L, cheques.get("compensado"));

        verify(boletoRepository).resumo(any(), any(), any(), any());
        verify(pagamentoPixRepository).resumo(any(), any(), any(), any());
        verify(chequeRepository).resumo(any(), any(), any(), any());
    }

    @Test
    void gerar_DeveFiltrarApenasBoletosQuandoTipoBoleto() {
        var rows = new ArrayList<Object[]>();
        rows.add(new Object[]{"PAGO", 1L, BigDecimal.valueOf(500)});
        when(boletoRepository.resumo(any(), any(), any(), any())).thenReturn(rows);

        var result = relatorioService.gerar(empresaId, de, ate, null, "BOLETO", null);

        assertEquals(BigDecimal.valueOf(500), result.get("totalGeral"));

        verify(boletoRepository).resumo(any(), any(), any(), any());
        verify(pagamentoPixRepository, never()).resumo(any(), any(), any(), any());
        verify(chequeRepository, never()).resumo(any(), any(), any(), any());
    }

    @Test
    void gerar_DeveRetornarZeradoQuandoNaoHaDados() {
        when(boletoRepository.resumo(any(), any(), any(), any())).thenReturn(new ArrayList<Object[]>());
        when(pagamentoPixRepository.resumo(any(), any(), any(), any())).thenReturn(new ArrayList<Object[]>());
        when(chequeRepository.resumo(any(), any(), any(), any())).thenReturn(new ArrayList<Object[]>());

        var result = relatorioService.gerar(empresaId, de, ate, null, "TODOS", null);

        assertEquals(BigDecimal.ZERO, result.get("totalGeral"));
    }

    @Test
    void gerar_DeveFiltrarApenasPagosQuandoStatusPago() {
        var boletosRows = new ArrayList<Object[]>();
        boletosRows.add(new Object[]{"PAGO", 2L, BigDecimal.valueOf(1000)});
        boletosRows.add(new Object[]{"PENDENTE", 1L, BigDecimal.valueOf(500)});
        when(boletoRepository.resumo(any(), any(), any(), any())).thenReturn(boletosRows);

        var pixRows = new ArrayList<Object[]>();
        pixRows.add(new Object[]{"PAGO", 1L, BigDecimal.valueOf(300)});
        when(pagamentoPixRepository.resumo(any(), any(), any(), any())).thenReturn(pixRows);

        var chequeRows = new ArrayList<Object[]>();
        chequeRows.add(new Object[]{"COMPENSADO", 1L, BigDecimal.valueOf(200)});
        chequeRows.add(new Object[]{"PENDENTE", 1L, BigDecimal.valueOf(100)});
        when(chequeRepository.resumo(any(), any(), any(), any())).thenReturn(chequeRows);

        var result = relatorioService.gerar(empresaId, de, ate, null, "TODOS", "PAGO");

        assertEquals(BigDecimal.valueOf(1500), result.get("totalGeral"));

        @SuppressWarnings("unchecked")
        var boletos = (java.util.Map<String, Object>) result.get("boletos");
        assertEquals(2L, boletos.get("total"));
        assertEquals(2L, boletos.get("pago"));
        assertEquals(0L, boletos.get("pendente"));

        @SuppressWarnings("unchecked")
        var cheques = (java.util.Map<String, Object>) result.get("cheques");
        assertEquals(1L, cheques.get("total"));
        assertEquals(1L, cheques.get("compensado"));
    }

    @Test
    void gerar_DeveFiltrarApenasPendentesQuandoStatusPendente() {
        var boletosRows = new ArrayList<Object[]>();
        boletosRows.add(new Object[]{"PAGO", 2L, BigDecimal.valueOf(1000)});
        boletosRows.add(new Object[]{"PENDENTE", 1L, BigDecimal.valueOf(500)});
        when(boletoRepository.resumo(any(), any(), any(), any())).thenReturn(boletosRows);

        var pixRows = new ArrayList<Object[]>();
        pixRows.add(new Object[]{"PAGO", 1L, BigDecimal.valueOf(300)});
        when(pagamentoPixRepository.resumo(any(), any(), any(), any())).thenReturn(pixRows);

        var chequeRows = new ArrayList<Object[]>();
        chequeRows.add(new Object[]{"COMPENSADO", 1L, BigDecimal.valueOf(200)});
        chequeRows.add(new Object[]{"PENDENTE", 1L, BigDecimal.valueOf(100)});
        when(chequeRepository.resumo(any(), any(), any(), any())).thenReturn(chequeRows);

        var result = relatorioService.gerar(empresaId, de, ate, null, "TODOS", "PENDENTE");

        assertEquals(BigDecimal.valueOf(600), result.get("totalGeral"));

        @SuppressWarnings("unchecked")
        var boletos = (java.util.Map<String, Object>) result.get("boletos");
        assertEquals(1L, boletos.get("total"));
        assertEquals(0L, boletos.get("pago"));
        assertEquals(1L, boletos.get("pendente"));

        @SuppressWarnings("unchecked")
        var cheques = (java.util.Map<String, Object>) result.get("cheques");
        assertEquals(1L, cheques.get("total"));
        assertEquals(1L, cheques.get("pendente"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void gerar_TodasAsLojas_QuebraPorLojaDoMaiorAPagarParaOMenor() {
        var centro = UUID.randomUUID();
        var norte = UUID.randomUUID();
        var boletos = new ArrayList<Object[]>();
        boletos.add(new Object[]{"PAGO", centro, "Centro", BigDecimal.valueOf(1000)});
        boletos.add(new Object[]{"PENDENTE", norte, "Norte", BigDecimal.valueOf(500)});
        boletos.add(new Object[]{"VENCIDO", norte, "Norte", BigDecimal.valueOf(300)});
        boletos.add(new Object[]{"CANCELADO", centro, "Centro", BigDecimal.valueOf(9999)});
        when(boletoRepository.resumoPorLoja(any(), any(), any())).thenReturn(boletos);
        var cheques = new ArrayList<Object[]>();
        cheques.add(new Object[]{"PENDENTE", centro, "Centro", BigDecimal.valueOf(200)});
        cheques.add(new Object[]{"DEVOLVIDO", centro, "Centro", BigDecimal.valueOf(50)});
        when(chequeRepository.resumoPorLoja(any(), any(), any())).thenReturn(cheques);

        var result = relatorioService.gerar(empresaId, de, ate, null, "TODOS", null);

        var porLoja = (java.util.List<java.util.Map<String, Object>>) result.get("porLoja");
        assertEquals(2, porLoja.size());
        assertEquals("Norte", porLoja.get(0).get("nome"));
        assertEquals(norte, porLoja.get(0).get("lojaId"));
        assertEquals(BigDecimal.ZERO, porLoja.get(0).get("valorPago"));
        assertEquals(BigDecimal.valueOf(800), porLoja.get(0).get("valorAPagar"));
        assertEquals(BigDecimal.valueOf(300), porLoja.get(0).get("valorEmAtraso"));
        assertEquals("Centro", porLoja.get(1).get("nome"));
        assertEquals(BigDecimal.valueOf(1000), porLoja.get(1).get("valorPago"));
        assertEquals(BigDecimal.valueOf(250), porLoja.get(1).get("valorAPagar"));
        assertEquals(BigDecimal.valueOf(50), porLoja.get(1).get("valorEmAtraso"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void gerar_TodasAsLojas_PorLojaRespeitaTipoEStatus() {
        var centro = UUID.randomUUID();
        var boletos = new ArrayList<Object[]>();
        boletos.add(new Object[]{"PAGO", centro, "Centro", BigDecimal.valueOf(1000)});
        boletos.add(new Object[]{"PENDENTE", centro, "Centro", BigDecimal.valueOf(500)});
        when(boletoRepository.resumoPorLoja(any(), any(), any())).thenReturn(boletos);

        var result = relatorioService.gerar(empresaId, de, ate, null, "BOLETO", "PAGO");

        var porLoja = (java.util.List<java.util.Map<String, Object>>) result.get("porLoja");
        assertEquals(BigDecimal.valueOf(1000), porLoja.get(0).get("valorPago"));
        assertEquals(BigDecimal.ZERO, porLoja.get(0).get("valorAPagar"));
        verify(chequeRepository, never()).resumoPorLoja(any(), any(), any());
        verify(pagamentoPixRepository, never()).resumoPorLoja(any(), any(), any());
    }

    @Test
    void gerar_UmaLojaSo_NaoQuebraPorLoja() {
        var result = relatorioService.gerar(empresaId, de, ate, UUID.randomUUID(), "TODOS", null);

        assertNull(result.get("porLoja"));
        verify(boletoRepository, never()).resumoPorLoja(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void gerar_CanceladoNaoEntraNoTotal_SoNaContagemDeCancelados() {
        var boletos = new ArrayList<Object[]>();
        boletos.add(new Object[]{"PAGO", 2L, BigDecimal.valueOf(1000)});
        boletos.add(new Object[]{"CANCELADO", 1L, BigDecimal.valueOf(400)});
        when(boletoRepository.resumo(any(), any(), any(), any())).thenReturn(boletos);
        var cheques = new ArrayList<Object[]>();
        cheques.add(new Object[]{"PENDENTE", 1L, BigDecimal.valueOf(100)});
        cheques.add(new Object[]{"CANCELADO", 1L, BigDecimal.valueOf(50)});
        when(chequeRepository.resumo(any(), any(), any(), any())).thenReturn(cheques);

        var result = relatorioService.gerar(empresaId, de, ate, null, "TODOS", null);

        assertEquals(BigDecimal.valueOf(1100), result.get("totalGeral"));
        var b = (java.util.Map<String, Object>) result.get("boletos");
        assertEquals(BigDecimal.valueOf(1000), b.get("valor"));
        assertEquals(2L, b.get("total"));
        assertEquals(1L, b.get("cancelado"));
        var c = (java.util.Map<String, Object>) result.get("cheques");
        assertEquals(BigDecimal.valueOf(100), c.get("valor"));
        assertEquals(1L, c.get("total"));
        assertEquals(1L, c.get("cancelado"));
    }
}
