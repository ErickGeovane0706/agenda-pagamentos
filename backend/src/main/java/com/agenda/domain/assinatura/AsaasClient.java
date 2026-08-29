package com.agenda.domain.assinatura;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Cliente HTTP da API do Asaas — cria o customer e a assinatura recorrente e
 * localiza a URL de pagamento da cobrança em aberto. É um wrapper fino: não
 * decide regra de negócio (valor, vínculo com a empresa), só fala HTTP.
 * <p>
 * Segurança:
 * <ul>
 *   <li>Autenticação pelo header {@code access_token} — nunca logado.</li>
 *   <li>Corpo das requisições carrega PII (CPF/CNPJ, telefone) — <b>nunca</b>
 *       logado. Em erro, loga só o caminho e a mensagem curta do gateway.</li>
 *   <li>Timeouts curtos (mesmo motivo do EmailService): o gateway não pode
 *       segurar a thread.</li>
 *   <li>Sem {@code ASAAS_API_KEY} configurada, falha rápido com mensagem clara
 *       em vez de bater na API e tomar 401.</li>
 * </ul>
 * O {@code base-url} vem da config e aponta para o sandbox por padrão; produção
 * troca a env {@code ASAAS_BASE_URL}.
 */
@Slf4j
@Component
public class AsaasClient {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final String USER_AGENT = "AgendaPagamentos";

    /** Status do Asaas em que a cobrança ainda pode ser paga. */
    private static final Set<String> EM_ABERTO = Set.of("PENDING", "OVERDUE");

    private final String baseUrl;
    private final String apiKey;
    private final RestTemplate restTemplate;

    @Autowired
    public AsaasClient(@Value("${asaas.base-url}") String baseUrl,
                       @Value("${asaas.api-key:}") String apiKey) {
        this(baseUrl, apiKey, criarRestTemplate());
    }

    /** Construtor de teste: injeta um RestTemplate para o MockRestServiceServer. */
    AsaasClient(String baseUrl, String apiKey, RestTemplate restTemplate) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.restTemplate = restTemplate;
    }

    private static RestTemplate criarRestTemplate() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    /**
     * Cria (ou registra) o cliente no Asaas. {@code cpfCnpj} e {@code telefone}
     * devem vir só com dígitos. Retorna o id do customer (ex.: {@code cus_...}).
     */
    public String criarCustomer(String nome, String cpfCnpj, String telefone,
                                String email, String externalReference) {
        var body = new LinkedHashMap<String, Object>();
        body.put("name", nome);
        body.put("cpfCnpj", cpfCnpj);
        body.put("mobilePhone", telefone);
        if (email != null && !email.isBlank()) {
            body.put("email", email);
        }
        body.put("externalReference", externalReference);
        return idObrigatorio(post("/customers", body));
    }

    /**
     * Cria a assinatura recorrente mensal. {@code billingType=UNDEFINED} deixa o
     * cliente escolher Pix ou boleto na página do Asaas. {@code externalReference}
     * = id da empresa, para o webhook casar o pagamento com o tenant certo.
     * Retorna o id da subscription (ex.: {@code sub_...}).
     */
    public String criarAssinatura(String customerId, BigDecimal valor,
                                  LocalDate proximoVencimento, String externalReference,
                                  String successUrl) {
        var body = new LinkedHashMap<String, Object>();
        body.put("customer", customerId);
        body.put("billingType", "UNDEFINED");
        body.put("value", valor);
        body.put("nextDueDate", proximoVencimento.toString());
        body.put("cycle", "MONTHLY");
        body.put("externalReference", externalReference);
        // Redireciona o cliente de volta ao app após o pagamento, em vez de
        // deixá-lo preso na página do Asaas. autoRedirect=true faz o pulo ser
        // automático. Só enviado quando há URL (sem frontend-url configurado,
        // omite — o Asaas então não redireciona, que é o comportamento antigo).
        if (successUrl != null && !successUrl.isBlank()) {
            var callback = new LinkedHashMap<String, Object>();
            callback.put("successUrl", successUrl);
            callback.put("autoRedirect", true);
            body.put("callback", callback);
            try {
                return idObrigatorio(post("/subscriptions", body));
            } catch (AsaasException e) {
                if (!(e.getCause() instanceof HttpClientErrorException.BadRequest)) {
                    throw e;
                }
                // O Asaas recusa com 400 uma successUrl de domínio diferente do
                // cadastrado em Minha Conta > Informações. O redirecionamento é só
                // conforto visual; deixar essa recusa derrubar o POST bloqueia toda
                // assinatura nova — foi o que travou as vendas em 27/08/2026.
                // 400 prova que nada foi criado, então repetir não duplica cobrança.
                log.error("[ASAAS] Assinatura recusada com callback — reenviando sem redirecionamento. "
                        + "Aponte app.frontend-url para o domínio cadastrado no Asaas.");
                body.remove("callback");
            }
        }
        return idObrigatorio(post("/subscriptions", body));
    }

    /**
     * Altera o valor mensal de uma assinatura já existente — usado quando muda a
     * quantidade de lojas contratadas. Sem isso o cliente seguiria pagando o preço
     * do dia em que assinou, por mais lojas que ganhasse.
     * <p>
     * O PUT do Asaas é parcial: enviar só {@code value} preserva ciclo, forma de
     * pagamento e vencimento. {@code updatePendingPayments=true} estende a mudança
     * à cobrança pendente já gerada — sem isso, o mês corrente ficaria no preço
     * antigo (ou, num downgrade, o cliente pagaria a mais por este mês).
     */
    public void atualizarValorAssinatura(String subscriptionId, BigDecimal valor) {
        var body = new LinkedHashMap<String, Object>();
        body.put("value", valor);
        body.put("updatePendingPayments", true);
        put("/subscriptions/" + subscriptionId, body);
    }

    /**
     * URL da página de pagamento ({@code invoiceUrl}) da cobrança EM ABERTO da
     * assinatura — é para onde o cliente é redirecionado para pagar.
     * Retorna {@code null} se não houver nenhuma cobrança em aberto.
     * <p>
     * O filtro por status não é detalhe: quem assina há meses tem uma lista de
     * cobranças, e as já pagas vêm antes. Enquanto isso só era chamado logo após
     * criar a assinatura — quando a lista tem um item só — pegar o primeiro dava
     * no mesmo; para o inadimplente, dava o recibo do mês que ele já pagou.
     */
    public String buscarUrlPagamento(String subscriptionId) {
        var resp = get("/subscriptions/" + subscriptionId + "/payments");
        var data = resp != null ? resp.path("data") : null;
        if (data != null && data.isArray()) {
            for (var pagamento : data) {
                if (!EM_ABERTO.contains(pagamento.path("status").asText(""))) {
                    continue;
                }
                var url = pagamento.path("invoiceUrl").asText(null);
                if (url != null && !url.isBlank()) {
                    return url;
                }
            }
        }
        return null;
    }

    /**
     * Cancela (remove) a assinatura no Asaas — para o cliente parar de ser
     * cobrado e para limpar assinaturas de teste criadas em produção.
     */
    public void cancelarAssinatura(String subscriptionId) {
        garantirConfigurado();
        try {
            restTemplate.exchange(baseUrl + "/subscriptions/" + subscriptionId,
                    HttpMethod.DELETE, new HttpEntity<>(headers()), Void.class);
        } catch (RestClientException e) {
            throw falha("/subscriptions/" + subscriptionId + " (DELETE)", e);
        }
    }

    // --- HTTP ---

    private JsonNode post(String path, Object body) {
        garantirConfigurado();
        try {
            return restTemplate.exchange(baseUrl + path, HttpMethod.POST,
                    new HttpEntity<>(body, headers()), JsonNode.class).getBody();
        } catch (RestClientException e) {
            throw falha(path, e);
        }
    }

    private JsonNode put(String path, Object body) {
        garantirConfigurado();
        try {
            return restTemplate.exchange(baseUrl + path, HttpMethod.PUT,
                    new HttpEntity<>(body, headers()), JsonNode.class).getBody();
        } catch (RestClientException e) {
            throw falha(path, e);
        }
    }

    private JsonNode get(String path) {
        garantirConfigurado();
        try {
            return restTemplate.exchange(baseUrl + path, HttpMethod.GET,
                    new HttpEntity<>(headers()), JsonNode.class).getBody();
        } catch (RestClientException e) {
            throw falha(path, e);
        }
    }

    private HttpHeaders headers() {
        var h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("access_token", apiKey);
        h.set(HttpHeaders.USER_AGENT, USER_AGENT);
        return h;
    }

    private void garantirConfigurado() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AsaasException("Gateway de pagamento não configurado (ASAAS_API_KEY ausente).");
        }
    }

    private String idObrigatorio(JsonNode resp) {
        var id = resp != null ? resp.path("id").asText(null) : null;
        if (id == null || id.isBlank()) {
            throw new AsaasException("Resposta do gateway sem o campo 'id'.");
        }
        return id;
    }

    /**
     * Loga só o caminho e a mensagem do erro (a mensagem do Asaas descreve a
     * falha de validação, sem CPF em claro) e devolve uma exceção com texto
     * genérico para o chamador — nunca vaza api-key nem corpo da requisição.
     */
    private AsaasException falha(String path, Exception e) {
        log.error("[ASAAS] Falha em POST/GET {}: {}", path, e.getMessage());
        return new AsaasException("Falha na comunicação com o gateway de pagamento.", e);
    }
}
