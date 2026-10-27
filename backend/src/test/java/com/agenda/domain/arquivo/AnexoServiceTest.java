package com.agenda.domain.arquivo;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnexoServiceTest {

    @Mock private AnexoRepository anexoRepository;
    @Mock private ArquivoService arquivoService;
    @Mock private AuditoriaService auditoriaService;

    private AnexoService anexoService;

    private final UUID boletoId = UUID.randomUUID();
    private final UUID lojaId = UUID.randomUUID();
    private final MockMultipartFile arquivo =
            new MockMultipartFile("arquivo", "nota-fiscal.pdf", "application/pdf", "%PDF-1.4".getBytes());

    @BeforeEach
    void setUp() {
        anexoService = new AnexoService(anexoRepository, arquivoService, auditoriaService);
    }

    private List<Anexo> anexosDoBoleto(int quantidade) {
        return IntStream.range(0, quantidade)
                .mapToObj(i -> Anexo.builder().id(UUID.randomUUID()).boletoId(boletoId)
                        .arquivoKey("boletos/" + lojaId + "/" + i + ".pdf").build())
                .toList();
    }

    @Test
    void adicionar_ComCincoJaAnexados_RecusaSemSubirNada() throws Exception {
        when(anexoRepository.findByBoletoIdOrderByCriadoEmAsc(boletoId)).thenReturn(anexosDoBoleto(5));

        var erro = assertThrows(IllegalArgumentException.class,
                () -> anexoService.adicionar(TipoDocumento.BOLETO, boletoId, lojaId, arquivo));

        assertTrue(erro.getMessage().contains("5"));
        verify(arquivoService, never()).upload(any(), any(), any());
        verify(anexoRepository, never()).save(any());
    }

    @Test
    void adicionar_ComQuatroAnexados_SalvaOQuintoNoLancamentoCerto() throws Exception {
        when(anexoRepository.findByBoletoIdOrderByCriadoEmAsc(boletoId)).thenReturn(anexosDoBoleto(4));
        when(arquivoService.upload(arquivo, TipoDocumento.BOLETO, lojaId)).thenReturn("boletos/x/novo.pdf");
        when(anexoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var dto = anexoService.adicionar(TipoDocumento.BOLETO, boletoId, lojaId, arquivo);

        var salvo = ArgumentCaptor.forClass(Anexo.class);
        verify(anexoRepository).save(salvo.capture());
        assertEquals(boletoId, salvo.getValue().getBoletoId());
        assertNull(salvo.getValue().getPixId());
        assertNull(salvo.getValue().getChequeId());
        assertEquals("boletos/x/novo.pdf", salvo.getValue().getArquivoKey());
        assertEquals("nota-fiscal.pdf", dto.nomeArquivo());
    }

    @Test
    void remover_AnexoDeOutroLancamento_DaNaoEncontradoENaoApaga() {
        var deOutroBoleto = Anexo.builder().id(UUID.randomUUID()).boletoId(UUID.randomUUID())
                .arquivoKey("boletos/outro.pdf").build();
        when(anexoRepository.findById(deOutroBoleto.getId())).thenReturn(Optional.of(deOutroBoleto));

        assertThrows(NotFoundException.class,
                () -> anexoService.remover(TipoDocumento.BOLETO, boletoId, deOutroBoleto.getId()));

        verify(arquivoService, never()).deletar(any());
        verify(anexoRepository, never()).delete(any());
    }

    @Test
    void gerarUrl_AnexoDePixPedidoComoBoleto_DaNaoEncontrado() {
        // Mesmo id de lançamento, outro tipo: o anexo é do PIX, não do boleto.
        var doPix = Anexo.builder().id(UUID.randomUUID()).pixId(boletoId).arquivoKey("pix/a.pdf").build();
        when(anexoRepository.findById(doPix.getId())).thenReturn(Optional.of(doPix));

        assertThrows(NotFoundException.class,
                () -> anexoService.gerarUrlTemporaria(TipoDocumento.BOLETO, boletoId, doPix.getId()));
        verify(arquivoService, never()).gerarUrlTemporaria(any());
    }

    @Test
    void removerArquivosDo_ApagaTodosDoBucket() {
        var anexos = anexosDoBoleto(3);
        when(anexoRepository.findByBoletoIdOrderByCriadoEmAsc(boletoId)).thenReturn(anexos);

        anexoService.removerArquivosDo(TipoDocumento.BOLETO, boletoId);

        anexos.forEach(a -> verify(arquivoService).deletar(a.getArquivoKey()));
    }

    @Test
    void removerArquivosDo_SemAnexos_NaoChamaOBucket() {
        when(anexoRepository.findByChequeIdOrderByCriadoEmAsc(boletoId)).thenReturn(Collections.emptyList());

        anexoService.removerArquivosDo(TipoDocumento.CHEQUE, boletoId);

        verifyNoInteractions(arquivoService);
    }
}
