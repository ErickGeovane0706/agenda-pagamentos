package com.agenda.domain.assinatura;

import com.agenda.domain.empresa.Empresa;
import com.agenda.shared.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Regras de acesso por assinatura. São exatamente DUAS checagens no sistema:
 * criar loja além do contratado (podeCriarLoja) e empresa inadimplente ou
 * cancelada em modo somente-leitura (podeAcessar) — nada de matriz de
 * entitlements por feature.
 *
 * A checagem é sempre lookup no banco (query por unique de empresa_id),
 * nunca claim no JWT: se o plano vivesse no token, o cliente pagaria e
 * continuaria bloqueado até relogar.
 */
@Slf4j
@Service
public class AssinaturaService {

    private final AssinaturaRepository assinaturaRepository;
    private final AsaasClient asaasClient;

    /**
     * Dias de tolerância após vigenteAte antes de bloquear o acesso.
     * Configurável porque a cobrança é por Pix/boleto manual — a compensação
     * pode levar dias.
     */
    private final int carenciaDias;

    /** Preço-base (inclui 1 loja) e valor por loja adicional — fonte do valor cobrado. */
    private final BigDecimal precoBase;
    private final BigDecimal precoLojaAdicional;

    public AssinaturaService(AssinaturaRepository assinaturaRepository,
                             AsaasClient asaasClient,
                             @Value("${assinatura.carencia-dias}") int carenciaDias,
                             @Value("${assinatura.preco-base}") BigDecimal precoBase,
                             @Value("${assinatura.preco-loja-adicional}") BigDecimal precoLojaAdicional) {
        this.assinaturaRepository = assinaturaRepository;
        this.asaasClient = asaasClient;
        this.carenciaDias = carenciaDias;
        this.precoBase = precoBase;
        this.precoLojaAdicional = precoLojaAdicional;
    }

    /**
     * A empresa pode operar (escrever, usar o agente, receber lembretes)?
     * TRIAL e ATIVA sim, desde que a vigência (quando controlada) não tenha
     * estourado a carência. Empresa sem assinatura não pode — não deve
     * acontecer (seed da V17 + trial criado junto com a empresa), mas se
     * acontecer o seguro é bloquear, não liberar.
     */
    @Transactional(readOnly = true)
    public boolean podeAcessar(UUID empresaId) {
        return assinaturaRepository.findByEmpresaId(empresaId)
            .map(this::acessivel)
            .orElse(false);
    }

    private boolean acessivel(Assinatura a) {
        if (a.getStatus() != StatusAssinatura.TRIAL && a.getStatus() != StatusAssinatura.ATIVA) {
            return false;
        }
        return a.getVigenteAte() == null
            || !LocalDate.now().isAfter(a.getVigenteAte().plusDays(carenciaDias));
    }

    /**
     * A empresa pode criar mais uma loja? Conta TODAS as lojas existentes
     * (não só as ativas — desativar/reativar não libera vaga).
     */
    @Transactional(readOnly = true)
    public boolean podeCriarLoja(UUID empresaId, long lojasExistentes) {
        return assinaturaRepository.findByEmpresaId(empresaId)
            .map(a -> lojasExistentes < a.getLojasContratadas())
            .orElse(false);
    }

    /**
     * Assinatura inicial de uma empresa recém-criada: TRIAL com 1 loja.
     * Sem isso a empresa nova nasceria sem assinatura e ficaria bloqueada
     * antes mesmo de criar a primeira loja. O MASTER ajusta depois.
     */
    @Transactional
    public Assinatura criarTrial(Empresa empresa) {
        return assinaturaRepository.save(Assinatura.builder()
            .empresa(empresa)
            .status(StatusAssinatura.TRIAL)
            .lojasContratadas(1)
            .build());
    }

    @Transactional(readOnly = true)
    public List<AssinaturaDTO> listar() {
        return assinaturaRepository.findAll().stream()
            .map(AssinaturaDTO::from)
            .toList();
    }

    /**
     * "Virada de linha na mão" da Fase 1: o MASTER ativa/suspende/cancela e
     * ajusta lojas contratadas e vigência após confirmar o pagamento do
     * Payment Link. Na Fase 2 o webhook do gateway passa a fazer isso.
     */
    @Transactional
    public AssinaturaDTO atualizar(UUID empresaId, AtualizarAssinaturaRequest req) {
        var assinatura = assinaturaRepository.findByEmpresaId(empresaId)
            .orElseThrow(() -> new NotFoundException("Assinatura não encontrada para a empresa"));

        assinatura.setStatus(req.status());
        assinatura.setLojasContratadas(req.lojasContratadas());
        assinatura.setVigenteAte(req.vigenteAte());
        if (req.gatewayCustomerId() != null && !req.gatewayCustomerId().isBlank()) {
            assinatura.setGatewayCustomerId(req.gatewayCustomerId());
        }
        return AssinaturaDTO.from(assinaturaRepository.save(assinatura));
    }

    /**
     * Inicia a assinatura recorrente da empresa no gateway: cria (ou reusa) o
     * customer e cria a subscription mensal amarrada à empresa pelo
     * {@code externalReference}. NÃO ativa a assinatura — isso acontece quando o
     * pagamento é confirmado pelo webhook. Idempotente: se já existe subscription,
     * devolve a URL de pagamento existente em vez de criar outra (evita cobrança
     * duplicada). Retorna o id da subscription e a URL de pagamento (Pix/boleto).
     */
    @Transactional
    public AssinaturaCheckoutDTO assinar(UUID empresaId) {
        var assinatura = assinaturaRepository.findByEmpresaId(empresaId)
            .orElseThrow(() -> new NotFoundException("Assinatura não encontrada para a empresa"));

        if (preenchido(assinatura.getGatewaySubscriptionId())) {
            var urlExistente = asaasClient.buscarUrlPagamento(assinatura.getGatewaySubscriptionId());
            return new AssinaturaCheckoutDTO(assinatura.getGatewaySubscriptionId(), urlExistente);
        }

        var empresa = assinatura.getEmpresa();
        exigirDadosDeCobranca(empresa);

        var customerId = assinatura.getGatewayCustomerId();
        if (!preenchido(customerId)) {
            customerId = asaasClient.criarCustomer(empresa.getNome(), empresa.getCpfCnpj(),
                empresa.getTelefone(), null, empresaId.toString());
            assinatura.setGatewayCustomerId(customerId);
        }

        var valor = calcularValor(assinatura.getLojasContratadas());
        var subscriptionId = asaasClient.criarAssinatura(customerId, valor,
            LocalDate.now(), empresaId.toString());
        assinatura.setGatewaySubscriptionId(subscriptionId);
        assinaturaRepository.save(assinatura);

        log.info("Assinatura criada no gateway para empresa {} (valor {})", empresaId, valor);
        return new AssinaturaCheckoutDTO(subscriptionId, asaasClient.buscarUrlPagamento(subscriptionId));
    }

    /**
     * Cancela a assinatura no gateway (para de cobrar) e marca CANCELADA. Usado
     * para encerrar a assinatura e para limpar assinaturas de teste em produção.
     */
    @Transactional
    public void cancelarAssinatura(UUID empresaId) {
        var assinatura = assinaturaRepository.findByEmpresaId(empresaId)
            .orElseThrow(() -> new NotFoundException("Assinatura não encontrada para a empresa"));
        if (preenchido(assinatura.getGatewaySubscriptionId())) {
            asaasClient.cancelarAssinatura(assinatura.getGatewaySubscriptionId());
            assinatura.setGatewaySubscriptionId(null);
        }
        assinatura.setStatus(StatusAssinatura.CANCELADA);
        assinaturaRepository.save(assinatura);
        log.info("Assinatura da empresa {} cancelada", empresaId);
    }

    /** Valor mensal: base (inclui 1 loja) + adicional por loja além da primeira. */
    BigDecimal calcularValor(int lojasContratadas) {
        int adicionais = Math.max(0, lojasContratadas - 1);
        return precoBase.add(precoLojaAdicional.multiply(BigDecimal.valueOf(adicionais)));
    }

    private void exigirDadosDeCobranca(Empresa empresa) {
        if (!preenchido(empresa.getCpfCnpj()) || !preenchido(empresa.getTelefone())) {
            throw new IllegalArgumentException(
                "Para assinar é preciso ter CPF/CNPJ e telefone cadastrados na empresa.");
        }
    }

    private boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }

    /**
     * Pagamento confirmado no gateway (webhook): ativa e estende a vigência
     * em 1 mês — a partir do fim da vigência atual se ela ainda está no
     * futuro (pagou adiantado), ou de hoje se já venceu/nunca teve.
     * Retorna false se nenhuma assinatura está vinculada a esse customer
     * (o chamador loga; o vínculo é feito pelo MASTER via PUT).
     */
    @Transactional
    public boolean registrarPagamentoConfirmado(String gatewayCustomerId) {
        return assinaturaRepository.findByGatewayCustomerId(gatewayCustomerId)
            .map(this::aplicarPagamentoConfirmado)
            .orElse(false);
    }

    /**
     * Igual a {@link #registrarPagamentoConfirmado}, mas resolve a assinatura
     * pela empresa (externalReference do webhook = empresaId) — o caminho
     * preferido, que não depende do customer estar pré-vinculado.
     */
    @Transactional
    public boolean registrarPagamentoConfirmadoPorEmpresa(UUID empresaId) {
        return assinaturaRepository.findByEmpresaId(empresaId)
            .map(this::aplicarPagamentoConfirmado)
            .orElse(false);
    }

    private boolean aplicarPagamentoConfirmado(Assinatura a) {
        var hoje = LocalDate.now();
        var base = a.getVigenteAte() != null && a.getVigenteAte().isAfter(hoje)
            ? a.getVigenteAte() : hoje;
        a.setVigenteAte(base.plusMonths(1));
        a.setStatus(StatusAssinatura.ATIVA);
        assinaturaRepository.save(a);
        log.info("Pagamento confirmado: empresa {} ativa até {}", a.getEmpresa().getId(), a.getVigenteAte());
        return true;
    }

    /**
     * Cobrança vencida no gateway (webhook): rebaixa para INADIMPLENTE.
     * Cancelada não regride (cancelamento é decisão deliberada do MASTER).
     */
    @Transactional
    public boolean registrarInadimplencia(String gatewayCustomerId) {
        return assinaturaRepository.findByGatewayCustomerId(gatewayCustomerId)
            .map(this::aplicarInadimplencia)
            .orElse(false);
    }

    /** Igual a {@link #registrarInadimplencia}, mas resolve pela empresa (externalReference). */
    @Transactional
    public boolean registrarInadimplenciaPorEmpresa(UUID empresaId) {
        return assinaturaRepository.findByEmpresaId(empresaId)
            .map(this::aplicarInadimplencia)
            .orElse(false);
    }

    private boolean aplicarInadimplencia(Assinatura a) {
        if (a.getStatus() != StatusAssinatura.CANCELADA) {
            a.setStatus(StatusAssinatura.INADIMPLENTE);
            assinaturaRepository.save(a);
            log.info("Cobrança vencida: empresa {} marcada INADIMPLENTE", a.getEmpresa().getId());
        }
        return true;
    }

    /**
     * Dunning mínimo: job diário que rebaixa para INADIMPLENTE as assinaturas
     * TRIAL/ATIVA cuja vigência estourou a carência. O bloqueio de acesso já
     * acontece antes disso via {@code podeAcessar} — este job só torna o
     * status visível/consistente (listagem do MASTER, webhook não entregue).
     */
    @Transactional
    @Scheduled(cron = "0 10 3 * * *")
    public void rebaixarVencidas() {
        var limite = LocalDate.now().minusDays(carenciaDias);
        var vencidas = assinaturaRepository.findByStatusInAndVigenteAteBefore(
            List.of(StatusAssinatura.TRIAL, StatusAssinatura.ATIVA), limite);
        for (var a : vencidas) {
            a.setStatus(StatusAssinatura.INADIMPLENTE);
            assinaturaRepository.save(a);
            log.info("Assinatura da empresa {} vencida além da carência — INADIMPLENTE", a.getEmpresa().getId());
        }
    }
}
