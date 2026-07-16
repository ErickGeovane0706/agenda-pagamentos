package com.agenda.domain.assinatura;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
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
                .andRespond(withSuccess("{\"id\":\"sub_abc\"}", MediaType.APPLICATION_JSON));

        var id = client.criarAssinatura("cus_123", new BigDecimal("137.00"),
                LocalDate.of(2026, 8, 15), "emp-1");

        assertThat(id).isEqualTo("sub_abc");
        server.verify();
    }

    @Test
    void buscarUrlPagamento_retornaInvoiceUrlDaPrimeiraCobranca() {
        server.expect(requestTo(BASE + "/subscriptions/sub_abc/payments"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"data\":[{\"invoiceUrl\":\"https://asaas.com/i/xyz\"}]}",
                        MediaType.APPLICATION_JSON));

        var url = client.buscarUrlPagamento("sub_abc");

        assertThat(url).isEqualTo("https://asaas.com/i/xyz");
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
