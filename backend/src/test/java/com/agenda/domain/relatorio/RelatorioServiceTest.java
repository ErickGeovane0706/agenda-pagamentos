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
}