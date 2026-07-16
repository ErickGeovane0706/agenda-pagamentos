package com.agenda.domain.assinatura;

import com.agenda.domain.webhook.WebhookIdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Roteamento e idempotência dos eventos de cobrança do Asaas.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AsaasWebhookProcessorTest {

    private static final UUID EMPRESA_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");

    @Mock private AssinaturaService assinaturaService;
    @Mock private WebhookIdempotencyService idempotencyService;
    @InjectMocks private AsaasWebhookProcessor processor;

    @BeforeEach
    void setUp() {
        when(idempotencyService.registrarSeNovo(any(), any())).thenReturn(true);
        when(assinaturaService.registrarPagamentoConfirmadoPorEmpresa(any())).thenReturn(true);
        when(assinaturaService.registrarInadimplenciaPorEmpresa(any(), any())).thenReturn(true);
    }

    private AsaasWebhookPayload evento(String eventoId, String tipo, String pagamentoId, String externalReference) {
        return new AsaasWebhookPayload(eventoId, tipo,
            new AsaasWebhookPayload.Payment(pagamentoId, "cus_123", externalReference));
    }

    @Test
    void pagamentoConfirmado_ComExternalReference_DeveAtivarPorEmpresa() {
        processor.processar(evento("evt_1", "PAYMENT_CONFIRMED", "pay_1", EMPRESA_ID.toString()));

        verify(assinaturaService).registrarPagamentoConfirmadoPorEmpresa(EMPRESA_ID);
        verify(assinaturaService, never()).registrarPagamentoConfirmado(any());
    }

    @Test
    void pagamentoConfirmado_SemExternalReference_DeveCairNoCustomer() {
        when(assinaturaService.registrarPagamentoConfirmado("cus_123")).thenReturn(true);

        processor.processar(evento("evt_1", "PAYMENT_CONFIRMED", "pay_1", null));

        verify(assinaturaService).registrarPagamentoConfirmado("cus_123");
    }

    /**
     * Regressão do bug que custava dinheiro: no boleto o Asaas dispara
     * PAYMENT_CONFIRMED e PAYMENT_RECEIVED para o MESMO pagamento, com ids de
     * evento diferentes. Deduplicar só por evento deixava os dois creditarem, e
     * o cliente ganhava dois meses por um pagamento.
     */
    @Test
    void boleto_ConfirmedEDepoisReceived_DoMesmoPagamento_DeveCreditarUmaVezSo() {
        when(idempotencyService.registrarSeNovo(AsaasWebhookProcessor.ORIGEM_CREDITO, "pay_1"))
            .thenReturn(true, false);

        processor.processar(evento("evt_1", "PAYMENT_CONFIRMED", "pay_1", EMPRESA_ID.toString()));
        processor.processar(evento("evt_2", "PAYMENT_RECEIVED", "pay_1", EMPRESA_ID.toString()));

        verify(assinaturaService, times(1)).registrarPagamentoConfirmadoPorEmpresa(EMPRESA_ID);
    }

    /**
     * O espelho do teste acima: a dedupe é do CRÉDITO, não do pagamento. Boleto
     * pago em atraso gera OVERDUE e depois CONFIRMED para o mesmo pagamento — se
     * o OVERDUE consumisse a chave, o cliente pagaria e nunca seria reativado.
     */
    @Test
    void boleto_PagoEmAtraso_OverduePrimeiro_DepoisConfirmed_DeveReativar() {
        processor.processar(evento("evt_1", "PAYMENT_OVERDUE", "pay_1", EMPRESA_ID.toString()));
        processor.processar(evento("evt_2", "PAYMENT_CONFIRMED", "pay_1", EMPRESA_ID.toString()));

        verify(assinaturaService).registrarInadimplenciaPorEmpresa(eq(EMPRESA_ID), any());
        verify(assinaturaService).registrarPagamentoConfirmadoPorEmpresa(EMPRESA_ID);
    }

    @Test
    void pix_SoRecebido_DeveCreditar() {
        processor.processar(evento("evt_1", "PAYMENT_RECEIVED", "pay_1", EMPRESA_ID.toString()));

        verify(assinaturaService).registrarPagamentoConfirmadoPorEmpresa(EMPRESA_ID);
    }

    @Test
    void eventoDuplicado_NaoDeveProcessarDeNovo() {
        when(idempotencyService.registrarSeNovo(AsaasWebhookProcessor.ORIGEM_EVENTO, "evt_1"))
            .thenReturn(false);

        processor.processar(evento("evt_1", "PAYMENT_CONFIRMED", "pay_1", EMPRESA_ID.toString()));

        verifyNoInteractions(assinaturaService);
    }

    @Test
    void cobrancaVencida_DeveMarcarInadimplente() {
        processor.processar(evento("evt_1", "PAYMENT_OVERDUE", "pay_1", EMPRESA_ID.toString()));

        verify(assinaturaService).registrarInadimplenciaPorEmpresa(eq(EMPRESA_ID), any());
    }

    @Test
    void estorno_DeveMarcarInadimplente() {
        processor.processar(evento("evt_1", "PAYMENT_REFUNDED", "pay_1", EMPRESA_ID.toString()));

        verify(assinaturaService).registrarInadimplenciaPorEmpresa(eq(EMPRESA_ID), any());
    }

    @Test
    void chargeback_DeveMarcarInadimplente() {
        processor.processar(evento("evt_1", "PAYMENT_CHARGEBACK_REQUESTED", "pay_1", EMPRESA_ID.toString()));

        verify(assinaturaService).registrarInadimplenciaPorEmpresa(eq(EMPRESA_ID), any());
    }

    @Test
    void cobrancaRemovida_DeveMarcarInadimplente() {
        processor.processar(evento("evt_1", "PAYMENT_DELETED", "pay_1", EMPRESA_ID.toString()));

        verify(assinaturaService).registrarInadimplenciaPorEmpresa(eq(EMPRESA_ID), any());
    }

    @Test
    void eventoNaoTratado_NaoDeveMexerNaAssinatura() {
        processor.processar(evento("evt_1", "PAYMENT_CREATED", "pay_1", EMPRESA_ID.toString()));

        verifyNoInteractions(assinaturaService);
    }

    @Test
    void eventoSemPayment_NaoDeveMexerNaAssinatura() {
        processor.processar(new AsaasWebhookPayload("evt_1", "PAYMENT_CONFIRMED", null));

        verifyNoInteractions(assinaturaService);
    }

    @Test
    void externalReferenceInvalido_DeveCairNoCustomer() {
        when(assinaturaService.registrarPagamentoConfirmado("cus_123")).thenReturn(true);

        processor.processar(evento("evt_1", "PAYMENT_CONFIRMED", "pay_1", "nao-e-uuid"));

        verify(assinaturaService).registrarPagamentoConfirmado("cus_123");
    }

    /**
     * Falha transitória tem de escapar para o controller responder 500 e a
     * transação voltar atrás — é o que garante que o reenvio do Asaas funcione.
     */
    @Test
    void falhaNoService_DevePropagarParaOControllerResponder500() {
        when(assinaturaService.registrarPagamentoConfirmadoPorEmpresa(any()))
            .thenThrow(new RuntimeException("banco fora"));

        assertThatThrownBy(() -> processor.processar(evento("evt_1", "PAYMENT_CONFIRMED", "pay_1", EMPRESA_ID.toString())))
            .isInstanceOf(RuntimeException.class);
    }
}
