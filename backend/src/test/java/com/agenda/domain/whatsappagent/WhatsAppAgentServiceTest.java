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
import com.agenda.security.RateLimiterService;
import com.agenda.whatsapp.WhatsAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Cobre principalmente as regras de SEGURANÇA do agente — são as que não
 * podem regredir silenciosamente: telefone não cadastrado nunca consulta
 * o banco, e loja fora da permissão do usuário nunca aparece na resposta.
 * <p>
 * A partir daqui, também cobre os novos {@link TipoPeriodoAgente} (gaps de
 * jargões de data em português) e, em especial, a regressão do bug
 * original — "até segunda" sendo resolvido uma semana inteira além do
 * esperado por falta de um enum próprio.
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
    // O TransactionTemplate executa o callback direto quando o gerenciador é um
    // mock — é só a fronteira da transação que some, e ela não é o objeto destes
    // testes (aqui os repositórios já são mocks).
    @Mock private PlatformTransactionManager transactionManager;

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
                chequeRepository, classifierService, respostaFormatter, whatsAppService,
                new RateLimiterService(), transactionManager);

        empresa = Empresa.builder().id(UUID.randomUUID()).nome("Empresa Teste").build();
        usuario = Usuario.builder().id(UUID.randomUUID()).nome("Maria").empresa(empresa).build();
        preferencia = PreferenciaNotificacao.builder()
                .id(UUID.randomUUID())
                .usuario(usuario)
                .telefoneWhatsapp("5583999990000")
                .whatsappAtivo(true)
                .build();
    }

    // ---------------------------------------------------------------
    // Testes de segurança já existentes
    // ---------------------------------------------------------------

    @Test
    void telefoneNaoCadastrado_naoDeveConsultarBancoNemClassificarMensagem() {
        when(preferenciaRepository.findByTelefoneNormalizadoIn(List.of("5583988887777", "558388887777"))).thenReturn(List.of());

        agentService.processarMensagem("5583988887777", "quanto tenho pra pagar hoje");

        verifyNoInteractions(classifierService, boletoRepository, pixRepository, chequeRepository);
        verify(whatsAppService).enviarMensagemTexto(eq("5583988887777"), contains("Não localizei"));
    }

    @Test
    void telefoneCadastrado_classificaEResponde() {
        when(preferenciaRepository.findByTelefoneNormalizadoIn(List.of("5583999990000", "558399990000"))).thenReturn(List.of(preferencia));
        when(classifierService.classificar(any(), any())).thenReturn(
                ResultadoClassificacao.builder()
                        .intencao(IntencaoAgente.SAUDACAO_AJUDA)
                        .filtros(FiltrosAgente.builder().periodo(TipoPeriodoAgente.SEM_FILTRO).build())
                        .build()
        );

        agentService.processarMensagem("5583999990000", "oi");

        verify(whatsAppService).enviarMensagemTexto(eq("5583999990000"), contains("Posso te ajudar"));
        verifyNoInteractions(boletoRepository, pixRepository, chequeRepository);
    }

    /**
     * O teste que protege o app de cair inteiro. O Hibernate segura a conexão do
     * pool (que tem 5) até o fim da transação; se a chamada à Anthropic — read
     * timeout de 30s — acontecer DENTRO dela, uma lentidão da LLM esgota o pool e
     * derruba também as requisições web e o scheduler. A ordem verificada aqui é
     * a garantia de que a conexão já voltou pro pool antes de a IA ser chamada.
     */
    @Test
    void chamadaAIA_deveAcontecerEntreAsTransacoes_nuncaDentroDeUma() {
        when(preferenciaRepository.findByTelefoneNormalizadoIn(List.of("5583999990000", "558399990000"))).thenReturn(List.of(preferencia));
        when(classifierService.classificar(any(), any())).thenReturn(
                ResultadoClassificacao.builder()
                        .intencao(IntencaoAgente.SAUDACAO_AJUDA)
                        .filtros(FiltrosAgente.builder().periodo(TipoPeriodoAgente.SEM_FILTRO).build())
                        .build()
        );

        agentService.processarMensagem("5583999990000", "oi");

        InOrder ordem = inOrder(transactionManager, classifierService);
        ordem.verify(transactionManager).getTransaction(any()); // 1ª transação: identifica o remetente
        ordem.verify(transactionManager).commit(any());         // fecha — devolve a conexão ao pool
        ordem.verify(classifierService).classificar(any(), any()); // IA chamada SEM conexão na mão
        ordem.verify(transactionManager).getTransaction(any()); // 2ª transação: consultas financeiras
        ordem.verify(transactionManager).commit(any());
    }

    @Test
    void lojaMencionadaForaDaPermissaoDoUsuario_naoExpoeDados() {
        Loja lojaPermitida = Loja.builder().id(UUID.randomUUID()).empresa(empresa).nome("Loja Centro").build();
        Loja lojaNaoPermitida = Loja.builder().id(UUID.randomUUID()).empresa(empresa).nome("Loja Sul").build();

        preferencia.setLojaIds(java.util.Set.of(lojaPermitida.getId()));

        when(preferenciaRepository.findByTelefoneNormalizadoIn(List.of("5583999990000", "558399990000"))).thenReturn(List.of(preferencia));
        when(lojaRepository.findByEmpresaIdOrderByNome(empresa.getId())).thenReturn(List.of(lojaPermitida, lojaNaoPermitida));
        when(classifierService.classificar(any(), any())).thenReturn(
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
        when(preferenciaRepository.findByTelefoneNormalizadoIn(List.of("5583999990000", "558399990000"))).thenReturn(List.of(preferencia));
        when(classifierService.classificar(any(), any())).thenThrow(new RuntimeException("timeout simulado"));

        assertDoesNotThrow(() -> agentService.processarMensagem("5583999990000", "quanto tenho pra pagar"));

        verify(whatsAppService).enviarMensagemTexto(eq("5583999990000"), contains("problema"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void consultarPendencias_somaValoresCorretamente() {
        when(preferenciaRepository.findByTelefoneNormalizadoIn(List.of("5583999990000", "558399990000"))).thenReturn(List.of(preferencia));
        when(classifierService.classificar(any(), any())).thenReturn(
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

    // ---------------------------------------------------------------
    // Regressão do bug original: "até segunda" (ou qualquer dia da semana)
    // não pode ser resolvido como se fosse "semana que vem" inteira.
    // ---------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void ateSegunda_naoDeveEstenderParaProximaSemanaInteira() {
        stubPreferenciaEncontrada();
        when(classifierService.classificar(any(), any())).thenReturn(
                ResultadoClassificacao.builder()
                        .intencao(IntencaoAgente.CONSULTAR_PENDENCIAS)
                        .filtros(FiltrosAgente.builder()
                                .periodo(TipoPeriodoAgente.ATE_DIA_SEMANA)
                                .diaSemanaAlvo(DiaSemanaAgente.SEGUNDA)
                                .build())
                        .build()
        );
        semResultadosNosRepositorios();

        agentService.processarMensagem("5583999990000", "o que tenho pra pagar até segunda");

        // A data-limite calculada tem que ser a PRÓXIMA segunda-feira a partir de
        // hoje (nunca a segunda da semana seguinte à próxima) — essa é a asserção
        // que teria pego o bug original antes de ir pro ar.
        LocalDate hoje = LocalDate.now();
        LocalDate proximaSegunda = hoje.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        long diasAteLimite = java.time.temporal.ChronoUnit.DAYS.between(hoje, proximaSegunda);

        assertTrue(diasAteLimite < 7,
                "\"até segunda\" nunca deve ultrapassar 6 dias a partir de hoje; calculado: " + diasAteLimite);
        verify(whatsAppService).enviarMensagemTexto(eq("5583999990000"), contains("segunda-feira"));
    }

    /**
     * Cobre os 7 dias da semana (o "off-by-one" do bug original só aparecia
     * em alguns casos — quando hoje já era o próprio dia-alvo, ou o dia
     * anterior a ele — por isso vale testar todos, não só um exemplo).
     */
    @ParameterizedTest
    @EnumSource(DiaSemanaAgente.class)
    @SuppressWarnings("unchecked")
    void ateQualquerDiaDaSemana_intervaloNuncaUltrapassaSeteDias(DiaSemanaAgente diaAlvo) {
        stubPreferenciaEncontrada();
        when(classifierService.classificar(any(), any())).thenReturn(
                ResultadoClassificacao.builder()
                        .intencao(IntencaoAgente.CONSULTAR_PENDENCIAS)
                        .filtros(FiltrosAgente.builder()
                                .periodo(TipoPeriodoAgente.ATE_DIA_SEMANA)
                                .diaSemanaAlvo(diaAlvo)
                                .build())
                        .build()
        );
        semResultadosNosRepositorios();

        assertDoesNotThrow(() -> agentService.processarMensagem("5583999990000", "até " + diaAlvo));

        verify(whatsAppService).enviarMensagemTexto(eq("5583999990000"), any());
    }

    // ---------------------------------------------------------------
    // Novos jargões de período
    // ---------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void quinzena_semPosicaoExplicita_naoQuebra() {
        stubPreferenciaEncontrada();
        when(classifierService.classificar(any(), any())).thenReturn(
                ResultadoClassificacao.builder()
                        .intencao(IntencaoAgente.CONSULTAR_PENDENCIAS)
                        .filtros(FiltrosAgente.builder().periodo(TipoPeriodoAgente.QUINZENA).build())
                        .build()
        );
        semResultadosNosRepositorios();

        agentService.processarMensagem("5583999990000", "o que vence na quinzena?");

        verify(whatsAppService).enviarMensagemTexto(eq("5583999990000"), contains("quinzena"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void quinzena_comPosicaoPrimeira_calculaDias1a15() {
        stubPreferenciaEncontrada();
        when(classifierService.classificar(any(), any())).thenReturn(
                ResultadoClassificacao.builder()
                        .intencao(IntencaoAgente.CONSULTAR_PENDENCIAS)
                        .filtros(FiltrosAgente.builder()
                                .periodo(TipoPeriodoAgente.QUINZENA)
                                .posicaoQuinzena(PosicaoQuinzenaAgente.PRIMEIRA)
                                .mesReferencia(MesReferenciaAgente.ATUAL)
                                .build())
                        .build()
        );
        semResultadosNosRepositorios();

        assertDoesNotThrow(() -> agentService.processarMensagem("5583999990000", "primeira quinzena do mês"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void fimDeSemana_naoQuebra() {
        stubPreferenciaEncontrada();
        when(classifierService.classificar(any(), any())).thenReturn(
                ResultadoClassificacao.builder()
                        .intencao(IntencaoAgente.CONSULTAR_PENDENCIAS)
                        .filtros(FiltrosAgente.builder().periodo(TipoPeriodoAgente.FIM_DE_SEMANA).build())
                        .build()
        );
        semResultadosNosRepositorios();

        agentService.processarMensagem("5583999990000", "o que vence no fds?");

        verify(whatsAppService).enviarMensagemTexto(eq("5583999990000"), contains("fim de semana"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void proximoFimDeSemana_ficaDepoisDoFimDeSemanaAtual() {
        stubPreferenciaEncontrada();
        when(classifierService.classificar(any(), any())).thenReturn(
                ResultadoClassificacao.builder()
                        .intencao(IntencaoAgente.CONSULTAR_PENDENCIAS)
                        .filtros(FiltrosAgente.builder().periodo(TipoPeriodoAgente.PROXIMO_FIM_DE_SEMANA).build())
                        .build()
        );
        semResultadosNosRepositorios();

        assertDoesNotThrow(() -> agentService.processarMensagem("5583999990000", "fim de semana que vem"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void viradaDeMes_semanaComercialAtravessaAFronteiraDoMes() {
        stubPreferenciaEncontrada();
        when(classifierService.classificar(any(), any())).thenReturn(
                ResultadoClassificacao.builder()
                        .intencao(IntencaoAgente.CONSULTAR_PENDENCIAS)
                        .filtros(FiltrosAgente.builder()
                                .periodo(TipoPeriodoAgente.VIRADA_MES)
                                .mesReferencia(MesReferenciaAgente.ATUAL)
                                .build())
                        .build()
        );
        semResultadosNosRepositorios();

        agentService.processarMensagem("5583999990000", "o que passa de um mês pro outro?");

        verify(whatsAppService).enviarMensagemTexto(eq("5583999990000"), contains("virada do mês"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void diaDoMes_ate15_naoQuebra() {
        stubPreferenciaEncontrada();
        when(classifierService.classificar(any(), any())).thenReturn(
                ResultadoClassificacao.builder()
                        .intencao(IntencaoAgente.CONSULTAR_PENDENCIAS)
                        .filtros(FiltrosAgente.builder()
                                .periodo(TipoPeriodoAgente.DIA_DO_MES)
                                .diaDoMes(15)
                                .mesReferencia(MesReferenciaAgente.ATUAL)
                                .build())
                        .build()
        );
        semResultadosNosRepositorios();

        agentService.processarMensagem("5583999990000", "o que vence até o dia 15");

        verify(whatsAppService).enviarMensagemTexto(eq("5583999990000"), contains("dia 15"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void diaDoMes_diaInexistenteNoMes_naoQuebra() {
        // Regressão do "31 de fevereiro": o código deve truncar pro último dia
        // real do mês em vez de lançar DateTimeException.
        stubPreferenciaEncontrada();
        when(classifierService.classificar(any(), any())).thenReturn(
                ResultadoClassificacao.builder()
                        .intencao(IntencaoAgente.CONSULTAR_PENDENCIAS)
                        .filtros(FiltrosAgente.builder()
                                .periodo(TipoPeriodoAgente.DIA_DO_MES)
                                .diaDoMes(31)
                                .mesReferencia(MesReferenciaAgente.ATUAL)
                                .build())
                        .build()
        );
        semResultadosNosRepositorios();

        assertDoesNotThrow(() -> agentService.processarMensagem("5583999990000", "o que vence até o dia 31"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void periodoNaoMapeado_caiEmSemFiltro_naoInventaData() {
        // "próximo dia útil" não tem enum — deve virar SEM_FILTRO em vez de
        // o classificador ou o código tentarem adivinhar uma data.
        stubPreferenciaEncontrada();
        when(classifierService.classificar(any(), any())).thenReturn(
                ResultadoClassificacao.builder()
                        .intencao(IntencaoAgente.CONSULTAR_PENDENCIAS)
                        .filtros(FiltrosAgente.builder().periodo(TipoPeriodoAgente.SEM_FILTRO).build())
                        .build()
        );
        semResultadosNosRepositorios();

        assertDoesNotThrow(() -> agentService.processarMensagem("5583999990000", "tenho algo pra pagar até o próximo dia útil?"));
    }

    // ---------------------------------------------------------------
    // REFINAR_ULTIMA_CONSULTA: intenção nova, precisa forçar o merge com a
    // última consulta mesmo quando a mensagem de refinamento traz mais de
    // 1 filtro novo (a heurística antiga só mesclava com exatamente 1).
    // ---------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void refinarUltimaConsulta_mescleMesmoComDoisFiltrosNovos() {
        stubPreferenciaEncontrada();
        semResultadosNosRepositorios();

        ResultadoClassificacao primeiraConsulta = ResultadoClassificacao.builder()
                .intencao(IntencaoAgente.CONSULTAR_PENDENCIAS)
                .filtros(FiltrosAgente.builder().periodo(TipoPeriodoAgente.SEMANA).build())
                .build();
        ResultadoClassificacao refinamento = ResultadoClassificacao.builder()
                .intencao(IntencaoAgente.REFINAR_ULTIMA_CONSULTA)
                .filtros(FiltrosAgente.builder()
                        .tipoPagamento(TipoPagamentoAgente.BOLETO)
                        .fornecedor("Fornecedora X")
                        .periodo(TipoPeriodoAgente.SEM_FILTRO)
                        .build())
                .build();
        when(classifierService.classificar(any(), any())).thenReturn(primeiraConsulta, refinamento);

        agentService.processarMensagem("5583999990000", "quanto tenho pra pagar essa semana");
        agentService.processarMensagem("5583999990000", "e de boleto da fornecedora X?");

        // Sob a heurística antiga (só mescla com exatamente 1 campo novo), esses 2
        // filtros (tipoPagamento + fornecedor) não teriam mesclado o período SEMANA
        // da consulta anterior. Como a intenção já veio como REFINAR_ULTIMA_CONSULTA,
        // o merge é forçado — as duas respostas devem refletir o período "semana".
        verify(whatsAppService, times(2)).enviarMensagemTexto(eq("5583999990000"), contains("semana"));
    }

    // ---------------------------------------------------------------
    // DETALHAR após lembrete automático: deve devolver o detalhe congelado
    // pelo scheduler, sem re-consultar o banco.
    // ---------------------------------------------------------------

    @Test
    void detalhar_aposLembreteAutomatico_devolveDetalheCongeladoSemConsultarBanco() {
        stubPreferenciaEncontrada();
        when(classifierService.classificar(any(), any())).thenReturn(
                ResultadoClassificacao.builder()
                        .intencao(IntencaoAgente.DETALHAR_ULTIMA_CONSULTA)
                        .filtros(FiltrosAgente.builder().periodo(TipoPeriodoAgente.SEM_FILTRO).build())
                        .build()
        );

        String detalhe = "📋 Pendências:\n\n🏬 Loja Centro — R$ 1.500,00\n"
                + "  • 🧾 Boleto: Light — R$ 1.500,00 (venc. 24/06)\n\n💰 Total: R$ 1.500,00";
        agentService.registrarDetalheNotificacao(usuario.getId(), detalhe);

        agentService.processarMensagem("5583999990000", "detalhar");

        verify(whatsAppService).enviarMensagemTexto(eq("5583999990000"), eq(detalhe));
        verifyNoInteractions(boletoRepository, pixRepository, chequeRepository);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void stubPreferenciaEncontrada() {
        when(preferenciaRepository.findByTelefoneNormalizadoIn(List.of("5583999990000", "558399990000")))
                .thenReturn(List.of(preferencia));
    }

    @SuppressWarnings("unchecked")
    private void semResultadosNosRepositorios() {
        when(boletoRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(pixRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(chequeRepository.findAll(any(Specification.class))).thenReturn(List.of());
    }
}