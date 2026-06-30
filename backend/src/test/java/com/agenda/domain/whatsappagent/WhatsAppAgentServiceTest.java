package com.agenda.domain.whatsappagent;

import com.agenda.domain.boleto.Boleto;
import com.agenda.domain.boleto.BoletoRepository;
import com.agenda.domain.cheque.ChequeRepository;
import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.loja.Loja;
import com.agenda.domain.loja.LojaRepository;
import com.agenda.domain.notificacao.PreferenciaNotificacao;
import com.agenda.domain.notificacao.PreferenciaNotificacaoRepository;
import com.agenda.domain.pix.PagamentoPixRepository;
import com.agenda.domain.usuario.Usuario;
import com.agenda.whatsapp.WhatsAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * Cobre principalmente as regras de SEGURANÇA do agente — são as que não
 * podem regredir silenciosamente: telefone não cadastrado nunca consulta
 * o banco, e loja fora da permissão do usuário nunca aparece na resposta.
 */
@ExtendWith(MockitoExtension.class)
class WhatsAppAgentServiceTest {

    @Mock private PreferenciaNotificacaoRepository preferenciaRepository;
    @Mock private LojaRepository lojaRepository;
    @Mock private BoletoRepository boletoRepository;
    @Mock private PagamentoPixRepository pixRepository;
    @Mock private ChequeRepository chequeRepository;
    @Mock private IntentClassifierService classifierService;
    @Mock private WhatsAppService whatsAppService;

    private WhatsAppAgentService agentService;
    private RespostaFormatterService respostaFormatter;

    private Empresa empresa;
    private Usuario usuario;
    private PreferenciaNotificacao preferencia;

    @BeforeEach
    void setUp() {
        respostaFormatter = new RespostaFormatterService();
        agentService = new WhatsAppAgentService(
                preferenciaRepository, lojaRepository, boletoRepository, pixRepository,
                chequeRepository, classifierService, respostaFormatter, whatsAppService);

        empresa = Empresa.builder().id(UUID.randomUUID()).nome("Empresa Teste").build();
        usuario = Usuario.builder().id(UUID.randomUUID()).nome("Maria").empresa(empresa).build();
        preferencia = PreferenciaNotificacao.builder()
                .id(UUID.randomUUID())
                .usuario(usuario)
                .telefoneWhatsapp("5583999990000")
                .whatsappAtivo(true)
                .build();
    }

    @Test
    void telefoneNaoCadastrado_naoDeveConsultarBancoNemClassificarMensagem() {
        when(preferenciaRepository.findByTelefoneNormalizado("5583988887777")).thenReturn(Optional.empty());

        agentService.processarMensagem("5583988887777", "quanto tenho pra pagar hoje");

        verifyNoInteractions(classifierService, boletoRepository, pixRepository, chequeRepository);
        verify(whatsAppService).enviarMensagemTexto(eq("5583988887777"), contains("não localizei"));
    }

    @Test
    void telefoneCadastrado_classificaEResponde() {
        when(preferenciaRepository.findByTelefoneNormalizado("5583999990000")).thenReturn(Optional.of(preferencia));
        when(classifierService.classificar(any())).thenReturn(
                ResultadoClassificacao.builder()
                        .intencao(IntencaoAgente.SAUDACAO_AJUDA)
                        .filtros(FiltrosAgente.builder().periodo(TipoPeriodoAgente.SEM_FILTRO).build())
                        .build()
        );

        agentService.processarMensagem("5583999990000", "oi");

        verify(whatsAppService).enviarMensagemTexto(eq("5583999990000"), contains("Posso te ajudar"));
        verifyNoInteractions(boletoRepository, pixRepository, chequeRepository);
    }

    @Test
    void lojaMencionadaForaDaPermissaoDoUsuario_naoExpoeDados() {
        Loja lojaPermitida = Loja.builder().id(UUID.randomUUID()).empresa(empresa).nome("Loja Centro").build();
        Loja lojaNaoPermitida = Loja.builder().id(UUID.randomUUID()).empresa(empresa).nome("Loja Sul").build();

        preferencia.setLojaIds(java.util.Set.of(lojaPermitida.getId()));

        when(preferenciaRepository.findByTelefoneNormalizado("5583999990000")).thenReturn(Optional.of(preferencia));
        when(lojaRepository.findByEmpresaIdOrderByNome(empresa.getId())).thenReturn(List.of(lojaPermitida, lojaNaoPermitida));
        when(classifierService.classificar(any())).thenReturn(
                ResultadoClassificacao.builder()
                        .intencao(IntencaoAgente.CONSULTAR_PENDENCIAS)
                        .filtros(FiltrosAgente.builder().loja("Loja Sul").periodo(TipoPeriodoAgente.HOJE).build())
                        .build()
        );

        agentService.processarMensagem("5583999990000", "quanto vence hoje na loja sul");

        verify(whatsAppService).enviarMensagemTexto(eq("5583999990000"), contains("não tem acesso"));
        verifyNoInteractions(boletoRepository, pixRepository, chequeRepository);
    }

    @Test
    void falhaNaClassificacao_respondeMensagemDeErroEmVezDeQuebrar() {
        when(preferenciaRepository.findByTelefoneNormalizado("5583999990000")).thenReturn(Optional.of(preferencia));
        when(classifierService.classificar(any())).thenThrow(new RuntimeException("timeout simulado"));

        assertDoesNotThrow(() -> agentService.processarMensagem("5583999990000", "quanto tenho pra pagar"));

        verify(whatsAppService).enviarMensagemTexto(eq("5583999990000"), contains("problema"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void consultarPendencias_somaValoresCorretamente() {
        when(preferenciaRepository.findByTelefoneNormalizado("5583999990000")).thenReturn(Optional.of(preferencia));
        when(classifierService.classificar(any())).thenReturn(
                ResultadoClassificacao.builder()
                        .intencao(IntencaoAgente.CONSULTAR_PENDENCIAS)
                        .filtros(FiltrosAgente.builder().periodo(TipoPeriodoAgente.HOJE).build())
                        .build()
        );

        Loja loja = Loja.builder().id(UUID.randomUUID()).empresa(empresa).nome("Loja Centro").build();
        Boleto boleto = Boleto.builder()
                .id(UUID.randomUUID()).empresa(empresa).loja(loja)
                .fornecedor("Fornecedor X").valor(new BigDecimal("150.00"))
                .vencimento(LocalDate.now())
                .build();

        when(boletoRepository.findAll(any(Specification.class))).thenReturn(List.of(boleto));
        when(pixRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(chequeRepository.findAll(any(Specification.class))).thenReturn(List.of());

        agentService.processarMensagem("5583999990000", "quanto vence hoje");

        verify(whatsAppService).enviarMensagemTexto(eq("5583999990000"), contains("150,00"));
    }
}
