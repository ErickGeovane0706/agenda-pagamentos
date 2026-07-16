package com.agenda.domain.assinatura;

import com.agenda.domain.webhook.WebhookIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Aplica o efeito de um evento de cobrança do Asaas. Existe separado do
 * controller para ter uma transação de verdade em volta: a marca de
 * idempotência e a mudança na assinatura precisam ser atômicas.
 * <p>
 * <b>Por que atômico importa:</b> se o evento fosse marcado como processado numa
 * transação e o efeito aplicado em outra, uma falha no meio deixaria o evento
 * marcado sem a assinatura ativada — e, como o Asaas não reenvia o que já
 * respondemos, o pagamento sumiria em silêncio: cliente paga, dinheiro entra, e
 * a empresa fica bloqueada para sempre. Aqui, se o efeito falha, a transação
 * volta atrás e a marca some junto, então o reenvio funciona.
 * <p>
 * <b>Dois níveis de deduplicação, por motivos diferentes:</b>
 * <ul>
 *   <li>por id do <b>evento</b> — protege de reentrega do mesmo evento;</li>
 *   <li>por id do <b>pagamento</b>, só no crédito de vigência — o Asaas dispara
 *       PAYMENT_CONFIRMED e PAYMENT_RECEIVED para o mesmo pagamento de boleto,
 *       com ids de evento diferentes. Sem isso, um pagamento vira dois meses.
 *       A dedupe é do <i>crédito</i>, não do pagamento: um boleto pago em atraso
 *       gera OVERDUE e depois CONFIRMED para o mesmo pagamento, e o CONFIRMED
 *       precisa passar para reativar o cliente.</li>
 * </ul>
 * Exceção que escapa daqui = falha transitória (banco fora): o controller
 * devolve não-200 e o Asaas reenvia. Falha permanente (empresa inexistente,
 * payload sem dados) apenas loga e retorna — reenviar não resolveria.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsaasWebhookProcessor {

    static final String ORIGEM_EVENTO = "ASAAS";
    static final String ORIGEM_CREDITO = "ASAAS_CREDITO";

    private final AssinaturaService assinaturaService;
    private final WebhookIdempotencyService idempotencyService;

    @Transactional
    public void processar(AsaasWebhookPayload payload) {
        if (payload.id() != null && !idempotencyService.registrarSeNovo(ORIGEM_EVENTO, payload.id())) {
            log.info("[ASAAS-WEBHOOK] Evento duplicado ignorado: {}", payload.id());
            return;
        }

        var pagamento = payload.payment();
        if (payload.event() == null || pagamento == null) {
            log.info("[ASAAS-WEBHOOK] Evento sem event/payment, ignorado: {}", payload.id());
            return;
        }

        switch (payload.event()) {
            case "PAYMENT_CONFIRMED", "PAYMENT_RECEIVED" -> creditar(pagamento);
            case "PAYMENT_OVERDUE" -> inadimplir(pagamento, "Cobrança vencida");
            case "PAYMENT_REFUNDED" -> inadimplir(pagamento, "Pagamento estornado");
            case "PAYMENT_CHARGEBACK_REQUESTED" -> inadimplir(pagamento, "Chargeback aberto");
            case "PAYMENT_DELETED" -> inadimplir(pagamento, "Cobrança removida no gateway");
            default -> log.debug("[ASAAS-WEBHOOK] Evento {} não tratado, ignorado.", payload.event());
        }
    }

    /**
     * Credita um mês de vigência e ativa. Só uma vez por pagamento — ver a nota
     * sobre PAYMENT_CONFIRMED/PAYMENT_RECEIVED no topo da classe.
     */
    private void creditar(AsaasWebhookPayload.Payment p) {
        if (p.id() != null && !idempotencyService.registrarSeNovo(ORIGEM_CREDITO, p.id())) {
            log.info("[ASAAS-WEBHOOK] Vigência do pagamento {} já creditada — evento ignorado.", p.id());
            return;
        }
        if (p.id() == null) {
            log.warn("[ASAAS-WEBHOOK] Pagamento sem id: creditando sem proteção contra duplo crédito.");
        }

        var empresaId = empresaIdDe(p);
        if (empresaId != null) {
            if (!assinaturaService.registrarPagamentoConfirmadoPorEmpresa(empresaId)) {
                log.warn("[ASAAS-WEBHOOK] Pagamento sem assinatura para empresa {} (externalReference).", empresaId);
            }
            return;
        }
        if (p.customer() != null) {
            if (!assinaturaService.registrarPagamentoConfirmado(p.customer())) {
                log.warn("[ASAAS-WEBHOOK] Pagamento do customer {} sem assinatura vinculada — "
                        + "vincular via PUT /api/assinaturas (gatewayCustomerId).", p.customer());
            }
            return;
        }
        log.warn("[ASAAS-WEBHOOK] Pagamento sem externalReference nem customer — ignorado.");
    }

    private void inadimplir(AsaasWebhookPayload.Payment p, String motivo) {
        var empresaId = empresaIdDe(p);
        if (empresaId != null) {
            if (!assinaturaService.registrarInadimplenciaPorEmpresa(empresaId, motivo)) {
                log.warn("[ASAAS-WEBHOOK] {} sem assinatura para empresa {} (externalReference).", motivo, empresaId);
            }
            return;
        }
        if (p.customer() != null) {
            if (!assinaturaService.registrarInadimplencia(p.customer(), motivo)) {
                log.warn("[ASAAS-WEBHOOK] {} do customer {} sem assinatura vinculada.", motivo, p.customer());
            }
            return;
        }
        log.warn("[ASAAS-WEBHOOK] {} sem externalReference nem customer — ignorado.", motivo);
    }

    /** empresaId a partir do externalReference; null se ausente ou não for um UUID. */
    private UUID empresaIdDe(AsaasWebhookPayload.Payment p) {
        var ref = p.externalReference();
        if (ref == null || ref.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(ref.trim());
        } catch (IllegalArgumentException e) {
            log.warn("[ASAAS-WEBHOOK] externalReference '{}' não é UUID de empresa — usando customer.", ref);
            return null;
        }
    }
}
