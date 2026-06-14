package com.agenda.domain.coluna;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.shared.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ColunaExtraServiceTest {

    @Mock private ColunaExtraRepository colunaExtraRepository;
    @Mock private ValorExtraRepository valorExtraRepository;
    @Mock private AuditoriaService auditoriaService;

    private ColunaExtraService colunaExtraService;
    private UUID empresaId;

    @BeforeEach
    void setUp() {
        colunaExtraService = new ColunaExtraService(colunaExtraRepository, valorExtraRepository, auditoriaService);
        empresaId = UUID.randomUUID();
        TenantContext.setEmpresaId(empresaId);
    }

    @Test
    void listar_DeveRetornarColunasAtivasDaEmpresa() {
        var coluna = ColunaExtra.builder()
            .id(UUID.randomUUID()).empresaId(empresaId)
            .tipoPagamento(TipoPagamento.BOLETO).nome("Número da nota")
            .tipoDado(TipoDado.TEXTO).ordem(0).ativo(true).build();

        when(colunaExtraRepository.findByEmpresaIdAndTipoPagamentoAndAtivoTrueOrderByOrdemAsc(
            empresaId, TipoPagamento.BOLETO)).thenReturn(List.of(coluna));

        var result = colunaExtraService.listar(TipoPagamento.BOLETO);

        assertEquals(1, result.size());
        assertEquals("Número da nota", result.get(0).nome());
    }

    @Test
    void criar_DeveSalvarColuna() {
        when(colunaExtraRepository.save(any())).thenAnswer(i -> {
            var c = i.getArgument(0, ColunaExtra.class);
            c.setId(UUID.randomUUID());
            return c;
        });

        var req = new CriarColunaExtraRequest(TipoPagamento.PIX, "CPF do recebedor",
            TipoDado.TEXTO, true, 1);
        var result = colunaExtraService.criar(req);

        assertEquals("CPF do recebedor", result.nome());
        assertTrue(result.obrigatorio());
        verify(colunaExtraRepository).save(any());
        verify(auditoriaService).registrar(eq("CRIAR"), eq("COLUNA_EXTRA"), any(), any());
    }

    @Test
    void salvarValores_DeveSubstituirValoresAntigos() {
        var registroId = UUID.randomUUID();

        colunaExtraService.salvarValores(TipoPagamento.BOLETO, registroId,
            Map.of(UUID.randomUUID(), "valor1"));

        verify(valorExtraRepository).deleteByRegistroIdAndTipoPagamento(registroId, TipoPagamento.BOLETO);
        verify(valorExtraRepository, atLeastOnce()).save(any());
    }

    @Test
    void excluir_DeveRemoverColuna() {
        var coluna = ColunaExtra.builder()
            .id(UUID.randomUUID()).empresaId(empresaId)
            .tipoPagamento(TipoPagamento.BOLETO).nome("Coluna X")
            .tipoDado(TipoDado.TEXTO).ativo(true).build();

        when(colunaExtraRepository.findById(coluna.getId())).thenReturn(Optional.of(coluna));

        colunaExtraService.excluir(coluna.getId());

        verify(colunaExtraRepository).delete(coluna);
        verify(auditoriaService).registrar(eq("EXCLUIR"), eq("COLUNA_EXTRA"), eq(coluna.getId()), anyString());
    }

    @Test
    void listarValores_DeveRetornarMapVazioQuandoNaoHaValores() {
        var registroId = UUID.randomUUID();
        when(valorExtraRepository.findByRegistroIdAndTipoPagamento(registroId, TipoPagamento.CHEQUE))
            .thenReturn(List.of());

        var result = colunaExtraService.listarValores(registroId, TipoPagamento.CHEQUE);

        assertTrue(result.isEmpty());
    }
}