package com.agenda.domain.whatsappagent;

import com.agenda.domain.webhook.WebhookIdempotencyService;
import com.agenda.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WhatsAppWebhookController.class)
class WhatsAppWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private WhatsAppAgentService agentService;
    @MockBean private WhatsAppSignatureValidator signatureValidator;
    @MockBean private WebhookIdempotencyService idempotencyService;
    @MockBean private JwtService jwtService;
    @MockBean private JpaMetamodelMappingContext jpaMappingContext;

    private static final String PAYLOAD =
        "{\"entry\":[{\"changes\":[{\"field\":\"messages\",\"value\":{\"messages\":["
        + "{\"id\":\"wamid.ABC\",\"from\":\"5583999999999\",\"type\":\"text\",\"text\":{\"body\":\"oi\"}}"
        + "]}}]}]}";

    @Test
    void post_ComAssinaturaValidaEMensagemNova_DeveProcessar() throws Exception {
        when(signatureValidator.isConfigurado()).thenReturn(true);
        when(signatureValidator.assinaturaValida(any(), any())).thenReturn(true);
        when(idempotencyService.registrarSeNovo("WHATSAPP", "wamid.ABC")).thenReturn(true);

        mockMvc.perform(post("/webhook/whatsapp")
                        .with(user("webhook")).with(csrf())
                        .header("X-Hub-Signature-256", "sha256=qualquer")
                        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isOk());

        verify(agentService).processarMensagem("5583999999999", "oi");
    }

    @Test
    void post_ComAssinaturaInvalida_DeveRetornar401ENaoProcessar() throws Exception {
        when(signatureValidator.isConfigurado()).thenReturn(true);
        when(signatureValidator.assinaturaValida(any(), any())).thenReturn(false);

        mockMvc.perform(post("/webhook/whatsapp")
                        .with(user("webhook")).with(csrf())
                        .header("X-Hub-Signature-256", "sha256=forjada")
                        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isUnauthorized());

        verify(agentService, never()).processarMensagem(any(), any());
    }

    @Test
    void post_MensagemDuplicada_DeveResponder200ENaoProcessar() throws Exception {
        when(signatureValidator.isConfigurado()).thenReturn(true);
        when(signatureValidator.assinaturaValida(any(), any())).thenReturn(true);
        when(idempotencyService.registrarSeNovo("WHATSAPP", "wamid.ABC")).thenReturn(false);

        mockMvc.perform(post("/webhook/whatsapp")
                        .with(user("webhook")).with(csrf())
                        .header("X-Hub-Signature-256", "sha256=qualquer")
                        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isOk());

        verify(agentService, never()).processarMensagem(any(), any());
    }
}
