package com.agenda.domain.assinatura;

import com.agenda.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre o que é responsabilidade do controller: autenticar o evento e traduzir
 * o resultado em status HTTP. O roteamento e a idempotência são do
 * {@link AsaasWebhookProcessor} — ver AsaasWebhookProcessorTest.
 */
@WebMvcTest(AsaasWebhookController.class)
@TestPropertySource(properties = "asaas.webhook-token=tok-teste")
class AsaasWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private AsaasWebhookProcessor processor;
    @MockBean private JwtService jwtService;
    @MockBean private JpaMetamodelMappingContext jpaMappingContext;
    /** Não usado aqui, mas o AssinaturaGateFilter entra no contexto e exige o bean. */
    @MockBean private AssinaturaService assinaturaService;

    private static final String PAYLOAD_CONFIRMADO =
        "{\"id\":\"evt_001\",\"event\":\"PAYMENT_CONFIRMED\",\"payment\":{\"id\":\"pay_1\",\"customer\":\"cus_123\"}}";

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postEvento(String payload, String token) {
        var req = post("/webhook/asaas")
            .with(user("webhook")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload);
        if (token != null) {
            req.header("asaas-access-token", token);
        }
        return req;
    }

    @Test
    void tokenValido_DeveProcessarEResponder200() throws Exception {
        mockMvc.perform(postEvento(PAYLOAD_CONFIRMADO, "tok-teste"))
            .andExpect(status().isOk());

        verify(processor).processar(any());
    }

    @Test
    void tokenInvalido_DeveRetornar401ENaoProcessar() throws Exception {
        mockMvc.perform(postEvento(PAYLOAD_CONFIRMADO, "tok-errado"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(processor);
    }

    @Test
    void semToken_DeveRetornar401ENaoProcessar() throws Exception {
        mockMvc.perform(postEvento(PAYLOAD_CONFIRMADO, null))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(processor);
    }

    /**
     * Falha transitória (banco fora) tem de devolver não-200: o Asaas só reenvia
     * o que não foi respondido com 200. Responder 200 aqui perderia o pagamento
     * para sempre — o cliente pagaria e ficaria bloqueado.
     */
    @Test
    void falhaNoProcessamento_DeveResponder500ParaOAsaasReenviar() throws Exception {
        doThrow(new RuntimeException("banco fora")).when(processor).processar(any());

        mockMvc.perform(postEvento(PAYLOAD_CONFIRMADO, "tok-teste"))
            .andExpect(status().isInternalServerError());
    }

    @Test
    void payloadIncompleto_NaoImpedeAutenticacao_EResponde200() throws Exception {
        mockMvc.perform(postEvento("{\"id\":\"evt_x\"}", "tok-teste"))
            .andExpect(status().isOk());

        verify(processor).processar(any());
    }
}
