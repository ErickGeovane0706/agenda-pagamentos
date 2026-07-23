package com.agenda.domain.assinatura;

import com.agenda.domain.empresa.Empresa;
import com.agenda.shared.exception.NotFoundException;
import com.agenda.shared.exception.PagamentoRequeridoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssinaturaServiceTest {

    private static final int CARENCIA_DIAS = 5;
    private static final int TRIAL_DIAS = 30;

    @Mock private AssinaturaRepository assinaturaRepository;
    @Mock private AsaasClient asaasClient;

    private AssinaturaService assinaturaService;
    private Empresa empresa;

    @BeforeEach
    void setUp() {
        assinaturaService = new AssinaturaService(assinaturaRepository, asaasClient, CARENCIA_DIAS,
            TRIAL_DIAS, new BigDecimal("79.00"), new BigDecimal("29.00"));
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

    /** O assinar lê pela query com JOIN FETCH — precisa da empresa fora de transação. */
    private void mockAssinaturaComEmpresa(Assinatura assinatura) {
        when(assinaturaRepository.findByEmpresaIdComEmpresa(empresa.getId()))
            .thenReturn(Optional.ofNullable(assinatura));
    }

    @Test
    void buscarMinha_DeveTrazerStatusEPrecosDaConfiguracao() {
        var vence = LocalDate.now().plusDays(10);
        mockAssinaturaComEmpresa(assinatura(StatusAssinatura.TRIAL, vence));

        var dto = assinaturaService.buscarMinha(empresa.getId());

        assertEquals(StatusAssinatura.TRIAL, dto.status());
        assertEquals(1, dto.lojasContratadas());
        assertEquals(vence, dto.vigenteAte());
        assertEquals(new BigDecimal("79.00"), dto.precoBase());
        assertEquals(new BigDecimal("29.00"), dto.precoLojaAdicional());
    }

    /** Quem veio do cadastro público não tem documento — a tela precisa saber para perguntar. */
    @Test
    void buscarMinha_SemCpfCnpj_DeveMarcarCobrancaIncompleta() {
        mockAssinaturaComEmpresa(assinatura(StatusAssinatura.TRIAL, LocalDate.now().plusDays(10)));
        assertFalse(assinaturaService.buscarMinha(empresa.getId()).dadosCobrancaCompletos());
    }

    @Test
    void buscarMinha_ComCpfCnpjETelefone_DeveMarcarCobrancaCompleta() {
        empresa.setCpfCnpj("12345678901");
        empresa.setTelefone("83999999999");
        mockAssinaturaComEmpresa(assinatura(StatusAssinatura.TRIAL, LocalDate.now().plusDays(10)));
        assertTrue(assinaturaService.buscarMinha(empresa.getId()).dadosCobrancaCompletos());
    }

    @Test
    void buscarMinha_SemAssinatura_DeveFalhar() {
        mockAssinaturaComEmpresa(null);
        assertThrows(RuntimeException.class, () -> assinaturaService.buscarMinha(empresa.getId()));
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
        mockAssinaturaComEmpresa(assinatura(StatusAssinatura.TRIAL, null));
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
        mockAssinaturaComEmpresa(existente);
        when(assinaturaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = assinaturaService.atualizar(empresa.getId(),
            new AtualizarAssinaturaRequest(StatusAssinatura.ATIVA, 2, null, null));

        assertEquals("cus_original", result.gatewayCustomerId());
    }

    @Test
    void atualizar_SemAssinatura_DeveLancarNotFound() {
        mockAssinaturaComEmpresa(null);
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

        assertTrue(assinaturaService.registrarInadimplencia("cus_123", "Cobrança vencida"));
        assertEquals(StatusAssinatura.INADIMPLENTE, a.getStatus());
    }

    @Test
    void registrarInadimplencia_Cancelada_NaoDeveRegredirStatus() {
        var a = assinaturaComGateway(StatusAssinatura.CANCELADA, null);

        assertTrue(assinaturaService.registrarInadimplencia("cus_123", "Cobrança vencida"));

        assertEquals(StatusAssinatura.CANCELADA, a.getStatus());
        verify(assinaturaRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // Trial
    // ---------------------------------------------------------------

    /**
     * Trial sem vigência é grátis para sempre: acessivel() libera quando a
     * vigência é nula e o rebaixarVencidas nunca o alcança. Com o cadastro
     * público, seria conta vitalícia para qualquer um.
     */
    @Test
    void criarTrial_DeveDefinirVigenciaPeloTrialDias() {
        when(assinaturaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var trial = assinaturaService.criarTrial(empresa);

        assertEquals(StatusAssinatura.TRIAL, trial.getStatus());
        assertEquals(LocalDate.now().plusDays(TRIAL_DIAS), trial.getVigenteAte());
    }

    @Test
    void podeAcessar_TrialVencidoAlemDaCarencia_DeveBloquear() {
        mockAssinatura(assinatura(StatusAssinatura.TRIAL,
            LocalDate.now().minusDays(CARENCIA_DIAS + 1)));

        assertFalse(assinaturaService.podeAcessar(empresa.getId()));
    }

    // ---------------------------------------------------------------
    // Painel do MASTER (atualizar)
    // ---------------------------------------------------------------

    /**
     * Mudar lojas contratadas muda o preço: sem propagar ao gateway, o cliente
     * ganha lojas e segue pagando o valor do dia em que assinou.
     */
    @Test
    void atualizar_MudandoLojas_DevePropagarNovoValorAoGateway() {
        var a = assinatura(StatusAssinatura.ATIVA, null);
        a.setGatewaySubscriptionId("sub_1");
        mockAssinaturaComEmpresa(a);

        assinaturaService.atualizar(empresa.getId(),
            new AtualizarAssinaturaRequest(StatusAssinatura.ATIVA, 3, null, null));

        verify(asaasClient).atualizarValorAssinatura("sub_1", new BigDecimal("137.00"));
    }

    @Test
    void atualizar_SemMudarLojas_NaoDeveChamarOGateway() {
        var a = assinatura(StatusAssinatura.ATIVA, null);
        a.setGatewaySubscriptionId("sub_1");
        mockAssinaturaComEmpresa(a);

        assinaturaService.atualizar(empresa.getId(),
            new AtualizarAssinaturaRequest(StatusAssinatura.ATIVA, 1, null, null));

        verify(asaasClient, never()).atualizarValorAssinatura(any(), any());
    }

    @Test
    void atualizar_MudandoLojasSemSubscription_NaoDeveChamarOGateway() {
        mockAssinaturaComEmpresa(assinatura(StatusAssinatura.TRIAL, null));

        assinaturaService.atualizar(empresa.getId(),
            new AtualizarAssinaturaRequest(StatusAssinatura.TRIAL, 3, null, null));

        verify(asaasClient, never()).atualizarValorAssinatura(any(), any());
    }

    /**
     * Cancelar pelo painel tem de parar a cobrança de verdade. Marcar CANCELADA
     * só no banco deixaria o Asaas cobrando, e o pagamento seguinte reativaria
     * a empresa.
     */
    @Test
    void atualizar_ParaCancelada_DeveCancelarNoGatewayELimparASubscription() {
        var a = assinatura(StatusAssinatura.ATIVA, null);
        a.setGatewaySubscriptionId("sub_1");
        mockAssinaturaComEmpresa(a);

        assinaturaService.atualizar(empresa.getId(),
            new AtualizarAssinaturaRequest(StatusAssinatura.CANCELADA, 1, null, null));

        verify(asaasClient).cancelarAssinatura("sub_1");
        assertNull(a.getGatewaySubscriptionId());
        assertEquals(StatusAssinatura.CANCELADA, a.getStatus());
    }

    /** Falha ao cancelar no gateway não pode gravar CANCELADA: perderíamos o id da subscription. */
    @Test
    void atualizar_ParaCancelada_FalhaNoGateway_NaoDeveGravar() {
        var a = assinatura(StatusAssinatura.ATIVA, null);
        a.setGatewaySubscriptionId("sub_1");
        mockAssinaturaComEmpresa(a);
        doThrow(new AsaasException("gateway fora")).when(asaasClient).cancelarAssinatura("sub_1");

        assertThrows(AsaasException.class, () -> assinaturaService.atualizar(empresa.getId(),
            new AtualizarAssinaturaRequest(StatusAssinatura.CANCELADA, 1, null, null)));

        verify(assinaturaRepository, never()).save(any());
        assertEquals("sub_1", a.getGatewaySubscriptionId());
    }

    // ---------------------------------------------------------------
    // Assinar / cancelar via API do gateway (passo 4)
    // ---------------------------------------------------------------

    @Test
    void calcularValor_UmaLoja_DeveSerSoABase() {
        assertEquals(new BigDecimal("79.00"), assinaturaService.calcularValor(1));
    }

    @Test
    void calcularValor_TresLojas_DeveSomarAdicionais() {
        assertEquals(new BigDecimal("137.00"), assinaturaService.calcularValor(3));
    }

    @Test
    void assinar_DeveCriarCustomerESubscriptionEGuardarIds() {
        empresa.setCpfCnpj("12345678901");
        empresa.setTelefone("11999999999");
        var a = assinatura(StatusAssinatura.TRIAL, null);
        a.setLojasContratadas(3);
        mockAssinaturaComEmpresa(a);
        when(asaasClient.criarCustomer(any(), any(), any(), any(), eq(empresa.getId().toString())))
            .thenReturn("cus_new");
        when(asaasClient.criarAssinatura(eq("cus_new"), eq(new BigDecimal("137.00")), any(),
                eq(empresa.getId().toString()))).thenReturn("sub_new");
        when(asaasClient.buscarUrlPagamento("sub_new")).thenReturn("https://asaas/i/1");
        when(assinaturaRepository.vincularSubscriptionSeAusente(empresa.getId(), "sub_new", "cus_new", 3))
            .thenReturn(1);

        var result = assinaturaService.assinar(empresa.getId(), null);

        assertEquals("sub_new", result.subscriptionId());
        assertEquals("https://asaas/i/1", result.urlPagamento());
        // lojas=3 gravado JUNTO com a subscription, não antes.
        verify(assinaturaRepository).vincularSubscriptionSeAusente(empresa.getId(), "sub_new", "cus_new", 3);
        assertEquals(StatusAssinatura.TRIAL, a.getStatus()); // não ativa antes do pagamento
    }

    /** O upsell da 2ª loja: assina já contratando duas, e o gateway cobra 79+29. */
    @Test
    void assinar_ComLojasDesejadas_DeveCobrarOAdicional() {
        empresa.setCpfCnpj("12345678901");
        empresa.setTelefone("11999999999");
        var a = assinatura(StatusAssinatura.TRIAL, null); // nasce com 1
        mockAssinaturaComEmpresa(a);
        when(asaasClient.criarCustomer(any(), any(), any(), any(), any())).thenReturn("cus_new");
        when(asaasClient.criarAssinatura(eq("cus_new"), eq(new BigDecimal("108.00")), any(), any()))
            .thenReturn("sub_new");
        when(asaasClient.buscarUrlPagamento("sub_new")).thenReturn("https://asaas/i/1");
        when(assinaturaRepository.vincularSubscriptionSeAusente(any(), any(), any(), anyInt())).thenReturn(1);

        assinaturaService.assinar(empresa.getId(), 2);

        // O gateway cobra por 2 lojas E lojas=2 é gravado junto com a subscription —
        // não antes, senão o gateway falhando deixaria 2 lojas sem cobrança.
        verify(asaasClient).criarAssinatura(eq("cus_new"), eq(new BigDecimal("108.00")), any(), any());
        verify(assinaturaRepository).vincularSubscriptionSeAusente(empresa.getId(), "sub_new", "cus_new", 2);
    }

    /** Pedir MENOS do que já tem contratado não reduz por este caminho. */
    @Test
    void assinar_ComLojasMenorQueOContratado_DeveIgnorar() {
        empresa.setCpfCnpj("12345678901");
        empresa.setTelefone("11999999999");
        var a = assinatura(StatusAssinatura.TRIAL, null);
        a.setLojasContratadas(3);
        mockAssinaturaComEmpresa(a);
        when(asaasClient.criarCustomer(any(), any(), any(), any(), any())).thenReturn("cus_new");
        when(asaasClient.criarAssinatura(any(), eq(new BigDecimal("137.00")), any(), any()))
            .thenReturn("sub_new");
        when(asaasClient.buscarUrlPagamento(any())).thenReturn("https://asaas/i/1");
        when(assinaturaRepository.vincularSubscriptionSeAusente(any(), any(), any(), anyInt())).thenReturn(1);

        assinaturaService.assinar(empresa.getId(), 1);

        // Pedir 1 quando já tem 3 não reduz: cobra e grava pelos 3 existentes.
        verify(assinaturaRepository).vincularSubscriptionSeAusente(empresa.getId(), "sub_new", "cus_new", 3);
    }

    @Test
    void contratarMaisLojas_DeveSubirQuantidadeEAtualizarValorNoGateway() {
        var a = assinatura(StatusAssinatura.ATIVA, null);
        a.setGatewaySubscriptionId("sub_exist");
        mockAssinaturaComEmpresa(a);
        when(assinaturaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = assinaturaService.contratarMaisLojas(empresa.getId(), 3);

        assertEquals(3, result.lojasContratadas());
        verify(asaasClient).atualizarValorAssinatura("sub_exist", new BigDecimal("137.00"));
    }

    /** Sem subscription, subir a quantidade seria liberar loja sem cobrança. */
    @Test
    void contratarMaisLojas_SemSubscription_DeveExigirPagamento() {
        mockAssinaturaComEmpresa(assinatura(StatusAssinatura.TRIAL, null));

        assertThrows(PagamentoRequeridoException.class,
            () -> assinaturaService.contratarMaisLojas(empresa.getId(), 2));
        verify(asaasClient, never()).atualizarValorAssinatura(any(), any());
    }

    @Test
    void contratarMaisLojas_Reduzindo_DeveRecusar() {
        var a = assinatura(StatusAssinatura.ATIVA, null);
        a.setLojasContratadas(4);
        a.setGatewaySubscriptionId("sub_exist");
        mockAssinaturaComEmpresa(a);

        assertThrows(IllegalArgumentException.class,
            () -> assinaturaService.contratarMaisLojas(empresa.getId(), 2));
        assertEquals(4, a.getLojasContratadas());
        verify(asaasClient, never()).atualizarValorAssinatura(any(), any());
    }

    @Test
    void assinar_SemCpfCnpj_DeveLancarIllegalArgumentSemChamarGateway() {
        empresa.setTelefone("11999999999"); // sem cpfCnpj
        mockAssinaturaComEmpresa(assinatura(StatusAssinatura.TRIAL, null));

        assertThrows(IllegalArgumentException.class, () -> assinaturaService.assinar(empresa.getId(), null));
        verify(asaasClient, never()).criarCustomer(any(), any(), any(), any(), any());
    }

    @Test
    void assinar_JaTemSubscription_NaoDeveCriarOutra() {
        var a = assinatura(StatusAssinatura.ATIVA, null);
        a.setGatewaySubscriptionId("sub_exist");
        mockAssinaturaComEmpresa(a);
        when(asaasClient.buscarUrlPagamento("sub_exist")).thenReturn("https://asaas/i/exist");

        var result = assinaturaService.assinar(empresa.getId(), null);

        assertEquals("sub_exist", result.subscriptionId());
        verify(asaasClient, never()).criarCustomer(any(), any(), any(), any(), any());
        verify(asaasClient, never()).criarAssinatura(any(), any(), any(), any());
    }

    @Test
    void assinar_ReusaCustomerExistente() {
        empresa.setCpfCnpj("12345678901");
        empresa.setTelefone("11999999999");
        var a = assinatura(StatusAssinatura.INADIMPLENTE, null);
        a.setGatewayCustomerId("cus_exist");
        mockAssinaturaComEmpresa(a);
        when(asaasClient.criarAssinatura(eq("cus_exist"), any(), any(), any())).thenReturn("sub_2");
        when(asaasClient.buscarUrlPagamento("sub_2")).thenReturn("https://asaas/i/2");
        when(assinaturaRepository.vincularSubscriptionSeAusente(any(), any(), any(), anyInt())).thenReturn(1);

        var result = assinaturaService.assinar(empresa.getId(), null);

        assertEquals("sub_2", result.subscriptionId());
        verify(asaasClient, never()).criarCustomer(any(), any(), any(), any(), any());
    }

    /**
     * Dois cliques simultâneos: o UPDATE condicional só deixa um vincular. Quem
     * perde PRECISA cancelar a subscription que criou no gateway — senão o
     * cliente fica com duas cobranças recorrentes, uma delas invisível ao sistema.
     */
    @Test
    void assinar_PerdendoACorrida_DeveCancelarASubscriptionOrfaEDevolverAVencedora() {
        empresa.setCpfCnpj("12345678901");
        empresa.setTelefone("11999999999");
        var a = assinatura(StatusAssinatura.TRIAL, null);
        a.setGatewayCustomerId("cus_exist");
        mockAssinaturaComEmpresa(a);
        when(asaasClient.criarAssinatura(any(), any(), any(), any())).thenReturn("sub_perdedora");
        when(assinaturaRepository.vincularSubscriptionSeAusente(any(), any(), any(), anyInt())).thenReturn(0);

        var vencedora = assinatura(StatusAssinatura.TRIAL, null);
        vencedora.setGatewaySubscriptionId("sub_vencedora");
        mockAssinatura(vencedora);
        when(asaasClient.buscarUrlPagamento("sub_vencedora")).thenReturn("https://asaas/i/vencedora");

        var result = assinaturaService.assinar(empresa.getId(), null);

        assertEquals("sub_vencedora", result.subscriptionId());
        verify(asaasClient).cancelarAssinatura("sub_perdedora");
    }

    /**
     * O customer precisa ser gravado assim que nasce. Se a subscription falhar
     * logo depois e o customer não tiver sido persistido, a retentativa cria
     * outro no gateway — foi assim que a base de sandbox ficou com dois customers
     * para o mesmo CPF.
     */
    @Test
    void assinar_FalhaAoCriarSubscription_DeveTerGravadoOCustomerParaARetentativa() {
        empresa.setCpfCnpj("12345678901");
        empresa.setTelefone("11999999999");
        mockAssinaturaComEmpresa(assinatura(StatusAssinatura.TRIAL, null));
        when(asaasClient.criarCustomer(any(), any(), any(), any(), any())).thenReturn("cus_novo");
        when(asaasClient.criarAssinatura(any(), any(), any(), any()))
            .thenThrow(new AsaasException("gateway fora"));

        assertThrows(AsaasException.class, () -> assinaturaService.assinar(empresa.getId(), null));

        verify(assinaturaRepository).vincularCustomerSeAusente(empresa.getId(), "cus_novo");
    }

    /**
     * O bug do "clique-e-erro": pedir 5 lojas e o gateway falhar NÃO pode gravar
     * lojasContratadas. Se gravasse (era o caso, num método não-transacional), o
     * teto liberava as lojas de graça — bastava assinar, tomar o erro e voltar.
     * A quantidade só é escrita em vincularSubscriptionSeAusente, que nem chega a
     * ser chamado quando o gateway falha antes.
     */
    @Test
    void assinar_GatewayFalha_NaoDevePersistirLojasContratadas() {
        empresa.setCpfCnpj("12345678901");
        empresa.setTelefone("11999999999");
        var a = assinatura(StatusAssinatura.TRIAL, null); // nasce com 1 loja
        mockAssinaturaComEmpresa(a);
        when(asaasClient.criarCustomer(any(), any(), any(), any(), any())).thenReturn("cus_novo");
        when(asaasClient.criarAssinatura(any(), any(), any(), any()))
            .thenThrow(new AsaasException("gateway 401"));

        assertThrows(AsaasException.class, () -> assinaturaService.assinar(empresa.getId(), 5));

        // Nunca vinculou (que é quem grava lojas), e o save direto do número sumiu.
        verify(assinaturaRepository, never()).vincularSubscriptionSeAusente(any(), any(), any(), anyInt());
        verify(assinaturaRepository, never()).save(any());
        assertEquals(1, a.getLojasContratadas());
    }

    @Test
    void assinar_CustomerJaGravado_NaoDeveCriarOutroNoGateway() {
        empresa.setCpfCnpj("12345678901");
        empresa.setTelefone("11999999999");
        var a = assinatura(StatusAssinatura.TRIAL, null);
        a.setGatewayCustomerId("cus_exist");
        mockAssinaturaComEmpresa(a);
        when(asaasClient.criarAssinatura(any(), any(), any(), any())).thenReturn("sub_1");
        when(assinaturaRepository.vincularSubscriptionSeAusente(any(), any(), any(), anyInt())).thenReturn(1);

        assinaturaService.assinar(empresa.getId(), null);

        verify(asaasClient, never()).criarCustomer(any(), any(), any(), any(), any());
        verify(assinaturaRepository, never()).vincularCustomerSeAusente(any(), any());
    }

    /**
     * Subscription criada no gateway mas não registrada aqui: sem compensar, o
     * Asaas cobraria o cliente por algo que o sistema desconhece.
     */
    @Test
    void assinar_FalhaAoVincular_DeveCancelarNoGatewayEPropagar() {
        empresa.setCpfCnpj("12345678901");
        empresa.setTelefone("11999999999");
        var a = assinatura(StatusAssinatura.TRIAL, null);
        a.setGatewayCustomerId("cus_exist");
        mockAssinaturaComEmpresa(a);
        when(asaasClient.criarAssinatura(any(), any(), any(), any())).thenReturn("sub_orfa");
        when(assinaturaRepository.vincularSubscriptionSeAusente(any(), any(), any(), anyInt()))
            .thenThrow(new RuntimeException("banco fora"));

        assertThrows(RuntimeException.class, () -> assinaturaService.assinar(empresa.getId(), null));

        verify(asaasClient).cancelarAssinatura("sub_orfa");
    }

    @Test
    void cancelarAssinatura_DeveChamarGatewayEMarcarCancelada() {
        var a = assinatura(StatusAssinatura.ATIVA, null);
        a.setGatewaySubscriptionId("sub_x");
        mockAssinatura(a);
        when(assinaturaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assinaturaService.cancelarAssinatura(empresa.getId());

        verify(asaasClient).cancelarAssinatura("sub_x");
        assertEquals(StatusAssinatura.CANCELADA, a.getStatus());
        assertNull(a.getGatewaySubscriptionId());
    }

    @Test
    void registrarPagamentoConfirmadoPorEmpresa_DeveAtivarEEstender() {
        var a = assinatura(StatusAssinatura.INADIMPLENTE, LocalDate.now().minusDays(3));
        mockAssinatura(a);
        when(assinaturaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertTrue(assinaturaService.registrarPagamentoConfirmadoPorEmpresa(empresa.getId()));

        assertEquals(StatusAssinatura.ATIVA, a.getStatus());
        assertEquals(LocalDate.now().plusMonths(1), a.getVigenteAte());
    }

    @Test
    void registrarPagamentoConfirmadoPorEmpresa_SemAssinatura_DeveRetornarFalse() {
        mockAssinatura(null);
        assertFalse(assinaturaService.registrarPagamentoConfirmadoPorEmpresa(empresa.getId()));
    }

    @Test
    void registrarInadimplenciaPorEmpresa_DeveRebaixarAtiva() {
        var a = assinatura(StatusAssinatura.ATIVA, null);
        mockAssinatura(a);
        when(assinaturaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertTrue(assinaturaService.registrarInadimplenciaPorEmpresa(empresa.getId(), "Pagamento estornado"));
        assertEquals(StatusAssinatura.INADIMPLENTE, a.getStatus());
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
