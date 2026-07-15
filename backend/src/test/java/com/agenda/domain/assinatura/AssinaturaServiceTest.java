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
import static org.mockito.ArgumentMatchers.any;
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
        var req = new AtualizarAssinaturaRequest(StatusAssinatura.ATIVA, 3, vigencia);

        var result = assinaturaService.atualizar(empresa.getId(), req);

        assertEquals(StatusAssinatura.ATIVA, result.status());
        assertEquals(3, result.lojasContratadas());
        assertEquals(vigencia, result.vigenteAte());
    }

    @Test
    void atualizar_SemAssinatura_DeveLancarNotFound() {
        mockAssinatura(null);
        var req = new AtualizarAssinaturaRequest(StatusAssinatura.ATIVA, 1, null);

        assertThrows(NotFoundException.class,
            () -> assinaturaService.atualizar(empresa.getId(), req));
    }
}
