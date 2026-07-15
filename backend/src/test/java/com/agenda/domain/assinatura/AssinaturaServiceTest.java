package com.agenda.domain.assinatura;

import com.agenda.domain.empresa.Empresa;
import com.agenda.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssinaturaServiceTest {

    private static final int CARENCIA_DIAS = 5;

    @Mock private AssinaturaRepository assinaturaRepository;

    private AssinaturaService assinaturaService;
    private Empresa empresa;

    @BeforeEach
    void setUp() {
        assinaturaService = new AssinaturaService(assinaturaRepository, CARENCIA_DIAS);
        empresa = Empresa.builder().id(UUID.randomUUID()).nome("Empresa Teste").build();
    }

    private Assinatura assinatura(StatusAssinatura status, LocalDate vigenteAte) {
        return Assinatura.builder()
            .id(UUID.randomUUID())
            .empresa(empresa)
            .status(status)
            .lojasContratadas(1)
            .vigenteAte(vigenteAte)
            .build();
    }

    private void mockAssinatura(Assinatura assinatura) {
        when(assinaturaRepository.findByEmpresaId(empresa.getId()))
            .thenReturn(Optional.ofNullable(assinatura));
    }

    @Test
    void podeAcessar_AtivaSemVigencia_DevePermitir() {
        mockAssinatura(assinatura(StatusAssinatura.ATIVA, null));
        assertTrue(assinaturaService.podeAcessar(empresa.getId()));
    }

    @Test
    void podeAcessar_Trial_DevePermitir() {
        mockAssinatura(assinatura(StatusAssinatura.TRIAL, null));
        assertTrue(assinaturaService.podeAcessar(empresa.getId()));
    }

    @Test
    void podeAcessar_Inadimplente_DeveBloquear() {
        mockAssinatura(assinatura(StatusAssinatura.INADIMPLENTE, null));
        assertFalse(assinaturaService.podeAcessar(empresa.getId()));
    }

    @Test
    void podeAcessar_Cancelada_DeveBloquear() {
        mockAssinatura(assinatura(StatusAssinatura.CANCELADA, null));
        assertFalse(assinaturaService.podeAcessar(empresa.getId()));
    }

    @Test
    void podeAcessar_VigenciaVencidaDentroDaCarencia_DevePermitir() {
        mockAssinatura(assinatura(StatusAssinatura.ATIVA, LocalDate.now().minusDays(CARENCIA_DIAS)));
        assertTrue(assinaturaService.podeAcessar(empresa.getId()));
    }

    @Test
    void podeAcessar_VigenciaVencidaAlemDaCarencia_DeveBloquear() {
        mockAssinatura(assinatura(StatusAssinatura.ATIVA, LocalDate.now().minusDays(CARENCIA_DIAS + 1)));
        assertFalse(assinaturaService.podeAcessar(empresa.getId()));
    }

    @Test
    void podeAcessar_SemAssinatura_DeveBloquear() {
        mockAssinatura(null);
        assertFalse(assinaturaService.podeAcessar(empresa.getId()));
    }

    @Test
    void podeCriarLoja_AbaixoDoContratado_DevePermitir() {
        mockAssinatura(assinatura(StatusAssinatura.ATIVA, null));
        assertTrue(assinaturaService.podeCriarLoja(empresa.getId(), 0));
    }

    @Test
    void podeCriarLoja_NoLimiteContratado_DeveBloquear() {
        mockAssinatura(assinatura(StatusAssinatura.ATIVA, null));
        assertFalse(assinaturaService.podeCriarLoja(empresa.getId(), 1));
    }

    @Test
    void podeCriarLoja_SemAssinatura_DeveBloquear() {
        mockAssinatura(null);
        assertFalse(assinaturaService.podeCriarLoja(empresa.getId(), 0));
    }

    @Test
    void criarTrial_DeveSalvarTrialComUmaLoja() {
        when(assinaturaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = assinaturaService.criarTrial(empresa);

        assertEquals(StatusAssinatura.TRIAL, result.getStatus());
        assertEquals(1, result.getLojasContratadas());
        assertEquals(empresa, result.getEmpresa());
    }

    @Test
    void atualizar_DeveAplicarStatusLojasEVigencia() {
        mockAssinatura(assinatura(StatusAssinatura.TRIAL, null));
        when(assinaturaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var vigencia = LocalDate.now().plusMonths(1);
        var req = new AtualizarAssinaturaRequest(StatusAssinatura.ATIVA, 3, vigencia, "cus_123");

        var result = assinaturaService.atualizar(empresa.getId(), req);

        assertEquals(StatusAssinatura.ATIVA, result.status());
        assertEquals(3, result.lojasContratadas());
        assertEquals(vigencia, result.vigenteAte());
        assertEquals("cus_123", result.gatewayCustomerId());
    }

    @Test
    void atualizar_SemGatewayCustomerId_NaoDeveLimparVinculoExistente() {
        var existente = assinatura(StatusAssinatura.ATIVA, null);
        existente.setGatewayCustomerId("cus_original");
        mockAssinatura(existente);
        when(assinaturaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = assinaturaService.atualizar(empresa.getId(),
            new AtualizarAssinaturaRequest(StatusAssinatura.ATIVA, 2, null, null));

        assertEquals("cus_original", result.gatewayCustomerId());
    }

    @Test
    void atualizar_SemAssinatura_DeveLancarNotFound() {
        mockAssinatura(null);
        var req = new AtualizarAssinaturaRequest(StatusAssinatura.ATIVA, 1, null, null);

        assertThrows(NotFoundException.class,
            () -> assinaturaService.atualizar(empresa.getId(), req));
    }

    // ---------------------------------------------------------------
    // Webhook do gateway (Fase 2)
    // ---------------------------------------------------------------

    private Assinatura assinaturaComGateway(StatusAssinatura status, LocalDate vigenteAte) {
        var a = assinatura(status, vigenteAte);
        a.setGatewayCustomerId("cus_123");
        when(assinaturaRepository.findByGatewayCustomerId("cus_123")).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    void registrarPagamentoConfirmado_VigenciaVencida_DeveAtivarEEstenderAPartirDeHoje() {
        var a = assinaturaComGateway(StatusAssinatura.INADIMPLENTE, LocalDate.now().minusDays(10));
        when(assinaturaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertTrue(assinaturaService.registrarPagamentoConfirmado("cus_123"));

        assertEquals(StatusAssinatura.ATIVA, a.getStatus());
        assertEquals(LocalDate.now().plusMonths(1), a.getVigenteAte());
    }

    @Test
    void registrarPagamentoConfirmado_VigenciaFutura_DeveEstenderAPartirDela() {
        var vigenciaFutura = LocalDate.now().plusDays(10);
        var a = assinaturaComGateway(StatusAssinatura.ATIVA, vigenciaFutura);
        when(assinaturaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertTrue(assinaturaService.registrarPagamentoConfirmado("cus_123"));

        assertEquals(vigenciaFutura.plusMonths(1), a.getVigenteAte());
    }

    @Test
    void registrarPagamentoConfirmado_CustomerDesconhecido_DeveRetornarFalse() {
        when(assinaturaRepository.findByGatewayCustomerId("cus_x")).thenReturn(Optional.empty());

        assertFalse(assinaturaService.registrarPagamentoConfirmado("cus_x"));
        verify(assinaturaRepository, never()).save(any());
    }

    @Test
    void registrarInadimplencia_DeveRebaixarAtiva() {
        var a = assinaturaComGateway(StatusAssinatura.ATIVA, null);
        when(assinaturaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertTrue(assinaturaService.registrarInadimplencia("cus_123"));
        assertEquals(StatusAssinatura.INADIMPLENTE, a.getStatus());
    }

    @Test
    void registrarInadimplencia_Cancelada_NaoDeveRegredirStatus() {
        var a = assinaturaComGateway(StatusAssinatura.CANCELADA, null);

        assertTrue(assinaturaService.registrarInadimplencia("cus_123"));

        assertEquals(StatusAssinatura.CANCELADA, a.getStatus());
        verify(assinaturaRepository, never()).save(any());
    }

    @Test
    void rebaixarVencidas_DeveMarcarInadimplenteQuemEstourouACarencia() {
        var vencida = assinatura(StatusAssinatura.ATIVA, LocalDate.now().minusDays(CARENCIA_DIAS + 10));
        when(assinaturaRepository.findByStatusInAndVigenteAteBefore(
                eq(java.util.List.of(StatusAssinatura.TRIAL, StatusAssinatura.ATIVA)),
                eq(LocalDate.now().minusDays(CARENCIA_DIAS))))
            .thenReturn(java.util.List.of(vencida));
        when(assinaturaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assinaturaService.rebaixarVencidas();

        assertEquals(StatusAssinatura.INADIMPLENTE, vencida.getStatus());
    }
}
