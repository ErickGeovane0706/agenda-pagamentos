package com.agenda.domain.assinatura;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cliente HTTP da API do Asaas — cria o customer e a assinatura recorrente e
 * localiza a URL de pagamento da primeira cobrança. É um wrapper fino: não
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
                                  LocalDate proximoVencimento, String externalReference) {
        var body = new LinkedHashMap<String, Object>();
        body.put("customer", customerId);
        body.put("billingType", "UNDEFINED");
        body.put("value", valor);
        body.put("nextDueDate", proximoVencimento.toString());
        body.put("cycle", "MONTHLY");
        body.put("externalReference", externalReference);
        return idObrigatorio(post("/subscriptions", body));
    }

    /**
     * URL da página de pagamento ({@code invoiceUrl}) da primeira cobrança gerada
     * pela assinatura — é para onde o cliente é redirecionado para pagar.
     * Retorna {@code null} se ainda não houver cobrança gerada.
     */
    public String buscarUrlPagamento(String subscriptionId) {
        var resp = get("/subscriptions/" + subscriptionId + "/payments");
        var data = resp != null ? resp.path("data") : null;
        if (data != null && data.isArray() && !data.isEmpty()) {
            var url = data.get(0).path("invoiceUrl").asText(null);
            return (url == null || url.isBlank()) ? null : url;
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
        return new AsaasException("Falha na comunicação com o gateway de pagamento.");
    }
}
