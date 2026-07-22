package com.agenda.domain.uso;

import com.agenda.domain.assinatura.Assinatura;
import com.agenda.domain.assinatura.AssinaturaRepository;
import com.agenda.domain.assinatura.StatusAssinatura;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O que estes testes protegem: o teto é a única coisa entre o cadastro público
 * e uma fatura sem limite na Meta/Anthropic/OpenAI. As regressões que importam
 * são silenciosas — passar o teto errado (o do pagante para um trial) ou avisar
 * o cliente a cada mensagem.
 */
@ExtendWith(MockitoExtension.class)
class LimiteUsoServiceTest {

    @Mock private UsoMensalEmpresaRepository usoRepository;
    @Mock private AssinaturaRepository assinaturaRepository;

    private LimiteUsoService service;

    private static final UUID EMPRESA_ID = UUID.randomUUID();

    private static final int TRIAL_TEMPLATES = 300;
    private static final int TRIAL_AGENTE = 400;
    private static final int TRIAL_AUDIOS = 60;
    private static final int ATIVA_TEMPLATES = 1500;
    private static final int ATIVA_AGENTE = 2000;
    private static final int ATIVA_AUDIOS = 300;

    @BeforeEach
    void setUp() {
        service = new LimiteUsoService(usoRepository, assinaturaRepository,
                TRIAL_TEMPLATES, TRIAL_AGENTE, TRIAL_AUDIOS,
                ATIVA_TEMPLATES, ATIVA_AGENTE, ATIVA_AUDIOS);
    }

    private void assinaturaCom(StatusAssinatura status, int lojasContratadas) {
        when(assinaturaRepository.findByEmpresaId(EMPRESA_ID)).thenReturn(Optional.of(
                Assinatura.builder().status(status).lojasContratadas(lojasContratadas).build()));
    }

    private void repositorioDevolve(int linhasAfetadas) {
        when(usoRepository.consumirSeAbaixoDoTeto(eq(EMPRESA_ID), eq(competenciaAtual()), eq(TipoUso.AGENTE.name()), anyInt()))
                .thenReturn(linhasAfetadas);
    }

    private String competenciaAtual() {
        return YearMonth.now().toString();
    }

    @Test
    void abaixoDoTeto_devePermitirOGasto() {
        assinaturaCom(StatusAssinatura.TRIAL, 1);
        repositorioDevolve(1);

        assertTrue(service.consumir(EMPRESA_ID, TipoUso.AGENTE));
    }

    @Test
    void tetoAtingido_deveBloquear() {
        assinaturaCom(StatusAssinatura.TRIAL, 1);
        repositorioDevolve(0);

        assertFalse(service.consumir(EMPRESA_ID, TipoUso.AGENTE));
    }

    /**
     * O teto apertado é o do TRIAL — é ele que qualquer bot consegue abrir
     * confirmando um email. Trocar por engano pelo do pagante multiplicaria o
     * custo de um tenant falso por cinco sem nenhum sintoma visível.
     */
    @Test
    void trial_deveUsarOTetoApertado() {
        assinaturaCom(StatusAssinatura.TRIAL, 1);
        repositorioDevolve(1);

        service.consumir(EMPRESA_ID, TipoUso.AGENTE);

        verify(usoRepository).consumirSeAbaixoDoTeto(
                EMPRESA_ID, competenciaAtual(), TipoUso.AGENTE.name(), TRIAL_AGENTE);
    }

    @Test
    void ativa_deveUsarOTetoFolgado() {
        assinaturaCom(StatusAssinatura.ATIVA, 1);
        repositorioDevolve(1);

        service.consumir(EMPRESA_ID, TipoUso.AGENTE);

        verify(usoRepository).consumirSeAbaixoDoTeto(
                EMPRESA_ID, competenciaAtual(), TipoUso.AGENTE.name(), ATIVA_AGENTE);
    }

    /**
     * Status que não é ATIVA nem TRIAL (inadimplente, cancelada) cai no teto
     * apertado. O acesso já foi barrado antes pelo {@code podeAcessar}; se algum
     * caminho novo esquecer esse gate, o seguro é o teto menor.
     */
    @Test
    void inadimplente_deveCairNoTetoApertado() {
        assinaturaCom(StatusAssinatura.INADIMPLENTE, 1);
        repositorioDevolve(1);

        service.consumir(EMPRESA_ID, TipoUso.AGENTE);

        verify(usoRepository).consumirSeAbaixoDoTeto(
                EMPRESA_ID, competenciaAtual(), TipoUso.AGENTE.name(), TRIAL_AGENTE);
    }

    @Test
    void empresaSemAssinatura_deveCairNoTetoApertado() {
        when(assinaturaRepository.findByEmpresaId(EMPRESA_ID)).thenReturn(Optional.empty());
        repositorioDevolve(1);

        service.consumir(EMPRESA_ID, TipoUso.AGENTE);

        verify(usoRepository).consumirSeAbaixoDoTeto(
                EMPRESA_ID, competenciaAtual(), TipoUso.AGENTE.name(), TRIAL_AGENTE);
    }

    /**
     * O aviso é um consumo com teto 1: quem "consegue consumir" é quem avisa.
     * Isso é o que impede um flood de virar um flood de respostas.
     */
    @Test
    void deveAvisar_usaTeto1NaCompetenciaAtual() {
        when(usoRepository.consumirSeAbaixoDoTeto(
                EMPRESA_ID, competenciaAtual(), TipoUso.AVISO_LIMITE.name(), 1)).thenReturn(1);

        assertTrue(service.deveAvisar(EMPRESA_ID));
    }

    @Test
    void deveAvisar_falsoQuandoJaAvisouNesteMes() {
        when(usoRepository.consumirSeAbaixoDoTeto(
                EMPRESA_ID, competenciaAtual(), TipoUso.AVISO_LIMITE.name(), 1)).thenReturn(0);

        assertFalse(service.deveAvisar(EMPRESA_ID));
    }

    /** 2 telefones na primeira loja, +1 a cada loja adicional. */
    @Test
    void limiteDestinatarios_deveSerLojasMaisUm() {
        assinaturaCom(StatusAssinatura.ATIVA, 1);
        assertEquals(2, service.limiteDestinatarios(EMPRESA_ID));
    }

    @Test
    void limiteDestinatarios_deveCrescerUmPorLojaAdicional() {
        assinaturaCom(StatusAssinatura.ATIVA, 3);
        assertEquals(4, service.limiteDestinatarios(EMPRESA_ID));
    }

    @Test
    void limiteDestinatarios_semAssinaturaDeveSerZero() {
        when(assinaturaRepository.findByEmpresaId(EMPRESA_ID)).thenReturn(Optional.empty());
        assertEquals(0, service.limiteDestinatarios(EMPRESA_ID));
    }
}
