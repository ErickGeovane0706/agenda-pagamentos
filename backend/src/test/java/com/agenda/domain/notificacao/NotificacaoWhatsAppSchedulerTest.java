package com.agenda.domain.notificacao;

import com.agenda.domain.boleto.Boleto;
import com.agenda.domain.boleto.BoletoRepository;
import com.agenda.domain.boleto.StatusBoleto;
import com.agenda.domain.cheque.ChequeRepository;
import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.loja.Loja;
import com.agenda.domain.pix.PagamentoPixRepository;
import com.agenda.domain.usuario.Usuario;
import com.agenda.whatsapp.WhatsAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacaoWhatsAppSchedulerTest {

    @Mock private PreferenciaNotificacaoRepository preferenciaRepository;
    @Mock private BoletoRepository boletoRepository;
    @Mock private PagamentoPixRepository pixRepository;
    @Mock private ChequeRepository chequeRepository;
    @Mock private WhatsAppService whatsAppService;

    private NotificacaoWhatsAppScheduler scheduler;

    private Empresa empresa;
    private Usuario usuario;
    private Loja loja;
    private final LocalDate hoje = LocalDate.of(2026, 6, 26); // sexta-feira
    private final LocalTime horario = LocalTime.of(9, 0);

    @BeforeEach
    void setUp() {
        scheduler = new NotificacaoWhatsAppScheduler(
                preferenciaRepository, boletoRepository, pixRepository, chequeRepository, whatsAppService);

        empresa = Empresa.builder().id(UUID.randomUUID()).nome("Empresa Teste").build();
        usuario = Usuario.builder().id(UUID.randomUUID()).nome("João").empresa(empresa).build();
        loja = Loja.builder().id(UUID.randomUUID()).empresa(empresa).nome("Mercado Central").build();
    }

    private PreferenciaNotificacao preferencia(LocalTime... horarios) {
        var builder = PreferenciaNotificacao.builder()
                .id(UUID.randomUUID())
                .usuario(usuario)
                .telefoneWhatsapp("+55 11 99999-0000")
                .whatsappAtivo(true)
                .lojaIds(Set.of(loja.getId()));

        LocalTime[] hs = horarios;
        if (hs.length > 0) builder.horario1(hs[0]);
        if (hs.length > 1) builder.horario2(hs[1]);
        if (hs.length > 2) builder.horario3(hs[2]);
        if (hs.length > 3) builder.horario4(hs[3]);

        return builder.build();
    }

    private Boleto boletoVencido(long diasAtraso) {
        return Boleto.builder()
                .id(UUID.randomUUID())
                .empresa(empresa)
                .loja(loja)
                .fornecedor("Light")
                .valor(new BigDecimal("1500.00"))
                .vencimento(hoje.minusDays(diasAtraso))
                .status(StatusBoleto.PENDENTE)
                .build();
    }

    private void semPixNemCheque() {
        when(pixRepository.findVencidos(any(), any())).thenReturn(List.of());
        when(pixRepository.findPendentesVencendoEm(any(), any())).thenReturn(List.of());
        when(pixRepository.findPendentesEntre(any(), any(), any())).thenReturn(List.of());
        when(chequeRepository.findVencidos(any(), any())).thenReturn(List.of());
        when(chequeRepository.findPendentesVencendoEm(any(), any())).thenReturn(List.of());
        when(chequeRepository.findPendentesEntre(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void semPendencias_NaoDeveEnviarNenhumaMensagem() {
        var pref = preferencia(horario);
        when(preferenciaRepository.findAtivosComHorario(horario)).thenReturn(List.of(pref));
        when(boletoRepository.findVencidos(any(), any())).thenReturn(List.of());
        when(boletoRepository.findPendentesVencendoEm(any(), any())).thenReturn(List.of());
        when(boletoRepository.findPendentesEntre(any(), any(), any())).thenReturn(List.of());
        semPixNemCheque();

        scheduler.verificarEEnviarNotificacoes();

        verifyNoInteractions(whatsAppService);
    }

    @Test
    void comPendenciaVencidaDentroDaFrequenciaNormal_DeveEnviarResumoMaisUmItem() {
        var pref = preferencia(horario);
        when(preferenciaRepository.findAtivosComHorario(horario)).thenReturn(List.of(pref));
        when(boletoRepository.findVencidos(any(), any())).thenReturn(List.of(boletoVencido(2)));
        when(boletoRepository.findPendentesVencendoEm(any(), any())).thenReturn(List.of());
        when(boletoRepository.findPendentesEntre(any(), any(), any())).thenReturn(List.of());
        semPixNemCheque();

        scheduler.verificarEEnviarNotificacoes();

        // 1 resumo + 1 item = 2 chamadas
        verify(whatsAppService, times(1)).enviarTemplate(
                eq(pref.getTelefoneWhatsapp()), eq(MensagemNotificacaoBuilder.TEMPLATE_RESUMO), anyList());
        verify(whatsAppService, times(1)).enviarTemplate(
                eq(pref.getTelefoneWhatsapp()), eq(MensagemNotificacaoBuilder.TEMPLATE_ITEM), anyList());
        verifyNoMoreInteractions(whatsAppService);
    }

    @Test
    void atrasoAcimaDe10Dias_SoDeveEnviarNoPrimeiroHorario() {
        LocalTime h1 = LocalTime.of(8, 0);
        LocalTime h2 = LocalTime.of(12, 0); // meio — não é primeiro nem último
        LocalTime h3 = LocalTime.of(18, 0);
        var pref = preferencia(h1, h2, h3);

        // Simula a execução do job exatamente no horário do meio (h2),
        // com 15 dias de atraso (>10 -> regra diz: só dispara no índice 0).
        when(preferenciaRepository.findAtivosComHorario(h2)).thenReturn(List.of(pref));
        when(boletoRepository.findVencidos(any(), any())).thenReturn(List.of(boletoVencido(15)));
        when(boletoRepository.findPendentesVencendoEm(any(), any())).thenReturn(List.of());
        when(boletoRepository.findPendentesEntre(any(), any(), any())).thenReturn(List.of());
        semPixNemCheque();

        scheduler.verificarEEnviarNotificacoes();

        // h2 é o horário do meio (índice 1 de 3) -> com atraso de 15 dias (>10),
        // só o índice 0 deveria disparar. Logo h2 não deve enviar nada.
        verifyNoInteractions(whatsAppService);
    }

    @Test
    void atrasoEntre4E10Dias_DeveEnviarNoPrimeiroEUltimoHorarioMasNaoNoMeio() {
        LocalTime h1 = LocalTime.of(8, 0);
        LocalTime h2 = LocalTime.of(12, 0); // meio
        LocalTime h3 = LocalTime.of(18, 0);
        var pref = preferencia(h1, h2, h3);

        when(preferenciaRepository.findAtivosComHorario(h3)).thenReturn(List.of(pref));
        when(boletoRepository.findVencidos(any(), any())).thenReturn(List.of(boletoVencido(7)));
        when(boletoRepository.findPendentesVencendoEm(any(), any())).thenReturn(List.of());
        when(boletoRepository.findPendentesEntre(any(), any(), any())).thenReturn(List.of());
        semPixNemCheque();

        scheduler.verificarEEnviarNotificacoes();

        // h3 é o ÚLTIMO horário -> com atraso de 7 dias (4-10), deve disparar.
        verify(whatsAppService, times(1)).enviarTemplate(
                eq(pref.getTelefoneWhatsapp()), eq(MensagemNotificacaoBuilder.TEMPLATE_RESUMO), anyList());
    }

    @Test
    void semLojasSelecionadas_NaoDeveEnviarEDeveSerIgnoradoSemErro() {
        var pref = preferencia(horario);
        pref.setLojaIds(Set.of());
        when(preferenciaRepository.findAtivosComHorario(horario)).thenReturn(List.of(pref));

        assertDoesNotThrow(scheduler::verificarEEnviarNotificacoes);

        verifyNoInteractions(whatsAppService);
        verifyNoInteractions(boletoRepository);
    }

    @Test
    void falhaEmUmUsuario_NaoDeveImpedirProcessamentoDosDemais() {
        var prefComErro = preferencia(horario);
        var prefOk = PreferenciaNotificacao.builder()
                .id(UUID.randomUUID())
                .usuario(Usuario.builder().id(UUID.randomUUID()).nome("Maria").empresa(empresa).build())
                .telefoneWhatsapp("+55 11 98888-0000")
                .whatsappAtivo(true)
                .lojaIds(Set.of(loja.getId()))
                .horario1(horario)
                .build();

        when(preferenciaRepository.findAtivosComHorario(horario)).thenReturn(List.of(prefComErro, prefOk));
        when(boletoRepository.findVencidos(any(), any()))
                .thenThrow(new RuntimeException("erro simulado"))
                .thenReturn(List.of(boletoVencido(1)));
        when(boletoRepository.findPendentesVencendoEm(any(), any())).thenReturn(List.of());
        when(boletoRepository.findPendentesEntre(any(), any(), any())).thenReturn(List.of());
        semPixNemCheque();

        assertDoesNotThrow(scheduler::verificarEEnviarNotificacoes);

        // prefOk ainda deve ter recebido resumo + item, mesmo com erro no primeiro usuário
        verify(whatsAppService, times(1)).enviarTemplate(
                eq(prefOk.getTelefoneWhatsapp()), eq(MensagemNotificacaoBuilder.TEMPLATE_RESUMO), anyList());
        verify(whatsAppService, times(1)).enviarTemplate(
                eq(prefOk.getTelefoneWhatsapp()), eq(MensagemNotificacaoBuilder.TEMPLATE_ITEM), anyList());
    }
}
