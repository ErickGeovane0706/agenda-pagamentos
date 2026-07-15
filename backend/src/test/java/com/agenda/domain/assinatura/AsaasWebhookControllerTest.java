package com.agenda.domain.assinatura;

import com.agenda.domain.webhook.WebhookIdempotencyService;
import com.agenda.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AsaasWebhookController.class)
@TestPropertySource(properties = "asaas.webhook-token=tok-teste")
class AsaasWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private AssinaturaService assinaturaService;
    @MockBean private WebhookIdempotencyService idempotencyService;
    @MockBean private JwtService jwtService;
    @MockBean private JpaMetamodelMappingContext jpaMappingContext;

    private static final String PAYLOAD_CONFIRMADO =
        "{\"id\":\"evt_001\",\"event\":\"PAYMENT_CONFIRMED\",\"payment\":{\"customer\":\"cus_123\",\"value\":137.00}}";

    private static final String PAYLOAD_VENCIDO =
        "{\"id\":\"evt_002\",\"event\":\"PAYMENT_OVERDUE\",\"payment\":{\"customer\":\"cus_123\"}}";

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
    void pagamentoConfirmado_ComTokenValido_DeveAtivarAssinatura() throws Exception {
        when(idempotencyService.registrarSeNovo("ASAAS", "evt_001")).thenReturn(true);
        when(assinaturaService.registrarPagamentoConfirmado("cus_123")).thenReturn(true);

        mockMvc.perform(postEvento(PAYLOAD_CONFIRMADO, "tok-teste"))
            .andExpect(status().isOk());

        verify(assinaturaService).registrarPagamentoConfirmado("cus_123");
    }

    @Test
    void cobrancaVencida_DeveMarcarInadimplencia() throws Exception {
        when(idempotencyService.registrarSeNovo("ASAAS", "evt_002")).thenReturn(true);
        when(assinaturaService.registrarInadimplencia("cus_123")).thenReturn(true);

        mockMvc.perform(postEvento(PAYLOAD_VENCIDO, "tok-teste"))
            .andExpect(status().isOk());

        verify(assinaturaService).registrarInadimplencia("cus_123");
    }

    @Test
    void tokenInvalido_DeveRetornar401ENaoProcessar() throws Exception {
        mockMvc.perform(postEvento(PAYLOAD_CONFIRMADO, "tok-errado"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(assinaturaService, idempotencyService);
    }

    @Test
    void semToken_DeveRetornar401() throws Exception {
        mockMvc.perform(postEvento(PAYLOAD_CONFIRMADO, null))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(assinaturaService);
    }

    @Test
    void eventoDuplicado_NaoDeveProcessarDeNovo() throws Exception {
        when(idempotencyService.registrarSeNovo("ASAAS", "evt_001")).thenReturn(false);

        mockMvc.perform(postEvento(PAYLOAD_CONFIRMADO, "tok-teste"))
            .andExpect(status().isOk());

        verifyNoInteractions(assinaturaService);
    }

    @Test
    void eventoNaoTratado_DeveResponder200SemProcessar() throws Exception {
        when(idempotencyService.registrarSeNovo(any(), any())).thenReturn(true);
        var payload = "{\"id\":\"evt_003\",\"event\":\"PAYMENT_CREATED\",\"payment\":{\"customer\":\"cus_123\"}}";

        mockMvc.perform(postEvento(payload, "tok-teste"))
            .andExpect(status().isOk());

        verifyNoInteractions(assinaturaService);
    }

    @Test
    void erroInternoNoProcessamento_DeveResponder200MesmoAssim() throws Exception {
        when(idempotencyService.registrarSeNovo(any(), any())).thenReturn(true);
        when(assinaturaService.registrarPagamentoConfirmado(any()))
            .thenThrow(new RuntimeException("erro simulado"));

        // 200 mesmo em erro: não-200 faz o Asaas interromper a fila de webhooks.
        mockMvc.perform(postEvento(PAYLOAD_CONFIRMADO, "tok-teste"))
            .andExpect(status().isOk());
    }
}
