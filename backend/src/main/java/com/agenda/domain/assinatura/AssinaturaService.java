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
import java.util.Objects;
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

    /** Duração do trial de uma empresa nova, em dias. */
    private final int trialDias;

    /** Preço-base (inclui 1 loja) e valor por loja adicional — fonte do valor cobrado. */
    private final BigDecimal precoBase;
    private final BigDecimal precoLojaAdicional;

    public AssinaturaService(AssinaturaRepository assinaturaRepository,
                             AsaasClient asaasClient,
                             @Value("${assinatura.carencia-dias}") int carenciaDias,
                             @Value("${assinatura.trial-dias}") int trialDias,
                             @Value("${assinatura.preco-base}") BigDecimal precoBase,
                             @Value("${assinatura.preco-loja-adicional}") BigDecimal precoLojaAdicional) {
        this.assinaturaRepository = assinaturaRepository;
        this.asaasClient = asaasClient;
        this.carenciaDias = carenciaDias;
        this.trialDias = trialDias;
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
     * Assinatura inicial de uma empresa recém-criada: TRIAL com 1 loja, vigente
     * por {@code assinatura.trial-dias}. Sem isso a empresa nova nasceria sem
     * assinatura e ficaria bloqueada antes mesmo de criar a primeira loja.
     * <p>
     * A vigência é obrigatória: {@code acessivel} trata vigência nula como
     * "sem controle de prazo", então um trial sem data seria grátis para sempre
     * e o {@code rebaixarVencidas} nunca o alcançaria (a query filtra por
     * vigenteAte). Com o cadastro público isso viraria conta vitalícia para
     * qualquer um que se registrasse.
     * <p>
     * Na prática o acesso dura {@code trial-dias + carencia-dias}: a carência é
     * aplicada de forma uniforme por {@code acessivel}. São alguns dias a mais
     * de cortesia, não um prazo indefinido.
     */
    @Transactional
    public Assinatura criarTrial(Empresa empresa) {
        return assinaturaRepository.save(Assinatura.builder()
            .empresa(empresa)
            .status(StatusAssinatura.TRIAL)
            .lojasContratadas(1)
            .vigenteAte(LocalDate.now().plusDays(trialDias))
            .build());
    }

    @Transactional(readOnly = true)
    public List<AssinaturaDTO> listar() {
        return assinaturaRepository.findAllComEmpresa().stream()
            .map(AssinaturaDTO::from)
            .toList();
    }

    /**
     * Painel do MASTER: ajusta status, lojas contratadas e vigência na mão, e
     * propaga ao gateway o que muda dinheiro. Não basta gravar aqui — a
     * assinatura no Asaas tem vida própria e continuaria cobrando o valor antigo
     * (ou cobrando uma assinatura "cancelada" só no nosso banco).
     * <p>
     * Sem {@code @Transactional}, como {@code assinar}: há chamadas de rede, que
     * não podem segurar conexão do pool.
     */
    public AssinaturaDTO atualizar(UUID empresaId, AtualizarAssinaturaRequest req) {
        var assinatura = assinaturaRepository.findByEmpresaIdComEmpresa(empresaId)
            .orElseThrow(() -> new NotFoundException("Assinatura não encontrada para a empresa"));

        var subscriptionId = assinatura.getGatewaySubscriptionId();
        var lojasMudaram = !Objects.equals(assinatura.getLojasContratadas(), req.lojasContratadas());

        // Cancelar: GATEWAY PRIMEIRO. Se gravássemos antes, o id da subscription
        // sumiria daqui e uma falha na chamada deixaria o Asaas cobrando para
        // sempre uma assinatura que ninguém mais consegue localizar.
        if (req.status() == StatusAssinatura.CANCELADA && preenchido(subscriptionId)) {
            asaasClient.cancelarAssinatura(subscriptionId);
            assinatura.setGatewaySubscriptionId(null);
            subscriptionId = null;
            log.info("Assinatura da empresa {} cancelada no gateway via painel", empresaId);
        }

        assinatura.setStatus(req.status());
        assinatura.setLojasContratadas(req.lojasContratadas());
        assinatura.setVigenteAte(req.vigenteAte());
        if (preenchido(req.gatewayCustomerId())) {
            assinatura.setGatewayCustomerId(req.gatewayCustomerId());
        }
        assinaturaRepository.save(assinatura);

        // Preço: BANCO PRIMEIRO. Se o gateway falhar, o MASTER recebe erro e repete
        // o PUT (idempotente). Enquanto isso cobra-se a menos, o que é preferível a
        // cobrar a mais por lojas que o banco ainda não liberou.
        if (lojasMudaram && preenchido(subscriptionId)) {
            var valor = calcularValor(req.lojasContratadas());
            asaasClient.atualizarValorAssinatura(subscriptionId, valor);
            log.info("Valor da assinatura da empresa {} atualizado para {} ({} lojas)",
                empresaId, valor, req.lojasContratadas());
        }
        return AssinaturaDTO.from(assinatura);
    }

    /**
     * Inicia a assinatura recorrente da empresa no gateway: cria (ou reusa) o
     * customer e cria a subscription mensal amarrada à empresa pelo
     * {@code externalReference}. NÃO ativa a assinatura — isso acontece quando o
     * pagamento é confirmado pelo webhook. Retorna o id da subscription e a URL
     * de pagamento (Pix/boleto).
     * <p>
     * <b>Sem {@code @Transactional} de propósito.</b> O método faz até três
     * chamadas HTTP ao Asaas (até 20s cada); com uma transação aberta em volta,
     * cada assinatura em curso seguraria uma conexão do pool (padrão: 5) durante
     * toda a espera — um gateway lento derrubaria o sistema inteiro, não só o
     * billing. Cada operação de banco aqui abre a sua própria transação curta.
     * <p>
     * Cobrança duplicada é evitada em dois níveis: o atalho para quem já tem
     * subscription, e o UPDATE condicional
     * ({@code vincularSubscriptionSeAusente}) que resolve a corrida entre dois
     * cliques simultâneos. Quem perde a corrida cancela no gateway a subscription
     * que criou — nada de cobrança órfã cobrando o cliente para sempre.
     */
    public AssinaturaCheckoutDTO assinar(UUID empresaId) {
        var assinatura = assinaturaRepository.findByEmpresaIdComEmpresa(empresaId)
            .orElseThrow(() -> new NotFoundException("Assinatura não encontrada para a empresa"));

        if (preenchido(assinatura.getGatewaySubscriptionId())) {
            return checkoutDe(assinatura.getGatewaySubscriptionId());
        }

        var empresa = assinatura.getEmpresa();
        exigirDadosDeCobranca(empresa);

        var customerId = assinatura.getGatewayCustomerId();
        if (!preenchido(customerId)) {
            customerId = asaasClient.criarCustomer(empresa.getNome(), empresa.getCpfCnpj(),
                empresa.getTelefone(), null, empresaId.toString());
            // Grava já: se a criação da subscription falhar logo abaixo, a retentativa
            // reusa este customer em vez de criar mais um no gateway.
            assinaturaRepository.vincularCustomerSeAusente(empresaId, customerId);
        }

        var valor = calcularValor(assinatura.getLojasContratadas());
        var subscriptionId = asaasClient.criarAssinatura(customerId, valor,
            LocalDate.now(), empresaId.toString());

        int vinculadas;
        try {
            vinculadas = assinaturaRepository.vincularSubscriptionSeAusente(empresaId, subscriptionId, customerId);
        } catch (RuntimeException e) {
            // A subscription existe no gateway mas não conseguimos registrá-la: sem
            // compensar, o cliente seria cobrado por algo que o sistema não conhece.
            compensarCancelando(subscriptionId, empresaId);
            throw e;
        }

        if (vinculadas == 0) {
            compensarCancelando(subscriptionId, empresaId);
            var vencedora = assinaturaRepository.findByEmpresaId(empresaId)
                .orElseThrow(() -> new NotFoundException("Assinatura não encontrada para a empresa"));
            log.info("Assinatura concorrente já vinculada para empresa {} — mantida a existente", empresaId);
            return checkoutDe(vencedora.getGatewaySubscriptionId());
        }

        log.info("Assinatura criada no gateway para empresa {} (valor {})", empresaId, valor);
        return checkoutDe(subscriptionId);
    }

    private AssinaturaCheckoutDTO checkoutDe(String subscriptionId) {
        return new AssinaturaCheckoutDTO(subscriptionId, asaasClient.buscarUrlPagamento(subscriptionId));
    }

    /**
     * Desfaz no gateway uma subscription que não ficou registrada aqui. Se a
     * compensação falhar, loga em ERROR e segue: a subscription órfã cobraria o
     * cliente indevidamente e precisa de remoção manual no painel.
     */
    private void compensarCancelando(String subscriptionId, UUID empresaId) {
        try {
            asaasClient.cancelarAssinatura(subscriptionId);
        } catch (RuntimeException e) {
            log.error("[ASAAS] Subscription {} da empresa {} ficou ÓRFÃ no gateway (cobrará indevidamente) "
                + "e o cancelamento automático falhou — remover no painel: {}", subscriptionId, empresaId, e.getMessage());
        }
    }

    /**
     * Cancela a assinatura no gateway (para de cobrar) e marca CANCELADA. Usado
     * para encerrar a assinatura e para limpar assinaturas de teste em produção.
     * <p>
     * Sem {@code @Transactional} pelo mesmo motivo do {@code assinar}: a chamada
     * ao gateway não pode segurar conexão do pool. O gateway vem primeiro de
     * propósito — se ele parar de cobrar e a gravação falhar, o cliente fica com
     * acesso sem pagar (recuperável); na ordem inversa ele ficaria bloqueado e
     * ainda sendo cobrado.
     */
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

    /**
     * Ativa sem checar CANCELADA de propósito — ao contrário do
     * {@link #aplicarInadimplencia}, que protege o cancelamento. Não é
     * esquecimento: {@code assinar} não mexe no status, então uma empresa
     * CANCELADA que assina de novo só volta a ATIVA quando o pagamento chega
     * aqui. Um guard travaria a re-assinatura — o cliente pagaria e continuaria
     * bloqueado. Cancelar sempre remove a subscription do gateway (tanto pelo
     * DELETE quanto pelo painel), então não há cobrança recorrente para
     * ressuscitar quem cancelou; e se um boleto pendente for pago mesmo assim,
     * liberar o acesso é o certo — o cliente pagou.
     */
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
     * Pagamento não honrado no gateway (webhook): rebaixa para INADIMPLENTE.
     * Serve tanto para cobrança vencida quanto para estorno/chargeback — em
     * todos, o dinheiro não está mais lá. {@code motivo} entra no log porque é
     * o que diferencia os casos numa investigação.
     * Cancelada não regride (cancelamento é decisão deliberada do MASTER).
     */
    @Transactional
    public boolean registrarInadimplencia(String gatewayCustomerId, String motivo) {
        return assinaturaRepository.findByGatewayCustomerId(gatewayCustomerId)
            .map(a -> aplicarInadimplencia(a, motivo))
            .orElse(false);
    }

    /** Igual a {@link #registrarInadimplencia}, mas resolve pela empresa (externalReference). */
    @Transactional
    public boolean registrarInadimplenciaPorEmpresa(UUID empresaId, String motivo) {
        return assinaturaRepository.findByEmpresaId(empresaId)
            .map(a -> aplicarInadimplencia(a, motivo))
            .orElse(false);
    }

    private boolean aplicarInadimplencia(Assinatura a, String motivo) {
        if (a.getStatus() != StatusAssinatura.CANCELADA) {
            a.setStatus(StatusAssinatura.INADIMPLENTE);
            assinaturaRepository.save(a);
            log.info("{}: empresa {} marcada INADIMPLENTE", motivo, a.getEmpresa().getId());
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
