package com.agenda.domain.assinatura;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verifica o contrato HTTP do AsaasClient sem tocar na API real (MockRestServiceServer):
 * header de autenticação, corpo enviado e parsing da resposta. Nenhum customer ou
 * cobrança de verdade é criado.
 */
class AsaasClientTest {

    private static final String BASE = "https://api.example/v3";
    private static final String KEY = "chave-secreta";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private AsaasClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new AsaasClient(BASE, KEY, restTemplate);
    }

    @Test
    void criarCustomer_enviaAuthEbody_eRetornaId() {
        server.expect(requestTo(BASE + "/customers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", KEY))
                .andExpect(jsonPath("$.name").value("Fulano"))
                .andExpect(jsonPath("$.cpfCnpj").value("12345678901"))
                .andExpect(jsonPath("$.mobilePhone").value("11999999999"))
                .andExpect(jsonPath("$.externalReference").value("emp-1"))
                .andRespond(withSuccess("{\"id\":\"cus_123\"}", MediaType.APPLICATION_JSON));

        var id = client.criarCustomer("Fulano", "12345678901", "11999999999", "f@x.com", "emp-1");

        assertThat(id).isEqualTo("cus_123");
        server.verify();
    }

    @Test
    void criarAssinatura_enviaCicloMensalEbillingUndefined_eRetornaId() {
        server.expect(requestTo(BASE + "/subscriptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", KEY))
                .andExpect(jsonPath("$.customer").value("cus_123"))
                .andExpect(jsonPath("$.billingType").value("UNDEFINED"))
                .andExpect(jsonPath("$.cycle").value("MONTHLY"))
                .andExpect(jsonPath("$.value").value(137.00))
                .andExpect(jsonPath("$.nextDueDate").value("2026-08-15"))
                .andExpect(jsonPath("$.externalReference").value("emp-1"))
                .andExpect(jsonPath("$.callback.successUrl").value("https://app.teste/lojas"))
                .andExpect(jsonPath("$.callback.autoRedirect").value(true))
                .andRespond(withSuccess("{\"id\":\"sub_abc\"}", MediaType.APPLICATION_JSON));

        var id = client.criarAssinatura("cus_123", new BigDecimal("137.00"),
                LocalDate.of(2026, 8, 15), "emp-1", "https://app.teste/lojas");

        assertThat(id).isEqualTo("sub_abc");
        server.verify();
    }

    /**
     * O Asaas recusa com 400 uma successUrl cujo domínio não seja o cadastrado em
     * Minha Conta > Informações. O redirecionamento é só conforto visual: se essa
     * recusa derrubar o POST inteiro, nenhum cliente novo consegue assinar — foi
     * exatamente o que aconteceu em produção em 27/08/2026.
     */
    @Test
    void criarAssinatura_quandoOgatewayRecusaOcallback_reenviaSemCallback() {
        server.expect(requestTo(BASE + "/subscriptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.callback.successUrl").value("https://dominio.errado/lojas"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errors\":[{\"code\":\"invalid_object\",\"description\":"
                                + "\"É necessário enviar uma URL que use o mesmo domínio cadastrado "
                                + "nas suas Minha Conta na aba Informações.\"}]}"));

        server.expect(requestTo(BASE + "/subscriptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.value").value(137.00))
                .andExpect(jsonPath("$.callback").doesNotExist())
                .andRespond(withSuccess("{\"id\":\"sub_abc\"}", MediaType.APPLICATION_JSON));

        var id = client.criarAssinatura("cus_123", new BigDecimal("137.00"),
                LocalDate.of(2026, 8, 15), "emp-1", "https://dominio.errado/lojas");

        assertThat(id).isEqualTo("sub_abc");
        server.verify();
    }

    /**
     * O reenvio vale só para o 400, que prova que nada foi criado. Numa falha de
     * rede a assinatura pode ter sido criada e só a resposta ter se perdido —
     * repetir o POST ali cobraria o cliente duas vezes.
     */
    @Test
    void criarAssinatura_quandoOgatewayFalhaPorRede_naoReenvia() {
        server.expect(requestTo(BASE + "/subscriptions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.criarAssinatura("cus_123", new BigDecimal("137.00"),
                LocalDate.of(2026, 8, 15), "emp-1", "https://dominio.errado/lojas"))
                .isInstanceOf(AsaasException.class);

        server.verify();
    }

    @Test
    void buscarUrlPagamento_retornaInvoiceUrlDaPrimeiraCobranca() {
        server.expect(requestTo(BASE + "/subscriptions/sub_abc/payments"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"data\":[{\"status\":\"PENDING\",\"invoiceUrl\":\"https://asaas.com/i/xyz\"}]}",
                        MediaType.APPLICATION_JSON));

        var url = client.buscarUrlPagamento("sub_abc");

        assertThat(url).isEqualTo("https://asaas.com/i/xyz");
        server.verify();
    }

    @Test
    void buscarUrlPagamento_assinanteAtrasado_pulaAsCobrancasJaPagas() {
        // Quem assina há meses tem várias cobranças, e o Asaas devolve as antigas
        // primeiro. Pegar data[0] mandaria o inadimplente para o RECIBO do mês que
        // ele já pagou, em vez do boleto que ele deve.
        server.expect(requestTo(BASE + "/subscriptions/sub_abc/payments"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"data\":["
                                + "{\"status\":\"RECEIVED\",\"invoiceUrl\":\"https://asaas.com/i/pago\"},"
                                + "{\"status\":\"OVERDUE\",\"invoiceUrl\":\"https://asaas.com/i/devendo\"}"
                                + "]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.buscarUrlPagamento("sub_abc")).isEqualTo("https://asaas.com/i/devendo");
        server.verify();
    }

    @Test
    void buscarUrlPagamento_semCobrancaAinda_retornaNull() {
        server.expect(requestTo(BASE + "/subscriptions/sub_abc/payments"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.buscarUrlPagamento("sub_abc")).isNull();
        server.verify();
    }

    @Test
    void recusaConhecida_viraMensagemAcionavel_semVazarOTextoDoGateway() {
        // O que derrubou a empresa Tetse em 29/08: fixo de 10 dígitos mandado no
        // campo mobilePhone. O gateway diz exatamente o que está errado, e o
        // cliente recebia "falha na comunicação" — genérica demais para agir.
        server.expect(requestTo(BASE + "/customers"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"errors\":[{\"code\":\"invalid_mobilePhone\",\"description\":\"TEXTO-CRU-DO-GATEWAY\"}]}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.criarCustomer("Tetse", "12345678909", "8399999999", null, "emp-1"))
                .isInstanceOf(AsaasException.class)
                .hasMessageContaining("celular")
                .hasMessageNotContaining("TEXTO-CRU-DO-GATEWAY");
        server.verify();
    }

    @Test
    void erroDoGateway_viraAsaasExceptionSanitizada() {
        server.expect(requestTo(BASE + "/customers"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
                        .body("{\"errors\":[{\"description\":\"cpfCnpj inválido\"}]}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.criarCustomer("Fulano", "0", "11999999999", null, "emp-1"))
                .isInstanceOf(AsaasException.class)
                .hasMessageContaining("gateway de pagamento");
        server.verify();
    }

    @Test
    void semApiKey_falhaRapidaSemChamarApi() {
        var semKey = new AsaasClient(BASE, "", restTemplate);

        assertThatThrownBy(() -> semKey.criarCustomer("Fulano", "12345678901", "11999999999", null, "emp-1"))
                .isInstanceOf(AsaasException.class)
                .hasMessageContaining("não configurado");
        server.verify(); // nenhuma requisição esperada
    }

    @Test
    void cancelarAssinatura_enviaDeleteComAuth() {
        server.expect(requestTo(BASE + "/subscriptions/sub_abc"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("access_token", KEY))
                .andRespond(withSuccess("{\"deleted\":true,\"id\":\"sub_abc\"}", MediaType.APPLICATION_JSON));

        client.cancelarAssinatura("sub_abc");
        server.verify();
    }
}
