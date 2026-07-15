package com.agenda.domain.whatsappagent;

import com.agenda.domain.assinatura.AssinaturaService;
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
    @MockBean private WhatsAppAudioService audioService;
    @MockBean private WhatsAppSignatureValidator signatureValidator;
    @MockBean private WebhookIdempotencyService idempotencyService;
    @MockBean private JwtService jwtService;
    @MockBean private JpaMetamodelMappingContext jpaMappingContext;
    // Exigido pelo AssinaturaGateFilter (@Component Filter, escaneado pelo @WebMvcTest).
    @MockBean private AssinaturaService assinaturaService;

    private static final String PAYLOAD =
        "{\"entry\":[{\"changes\":[{\"field\":\"messages\",\"value\":{\"messages\":["
        + "{\"id\":\"wamid.ABC\",\"from\":\"5583999999999\",\"type\":\"text\",\"text\":{\"body\":\"oi\"}}"
        + "]}}]}]}";

    private static final String PAYLOAD_AUDIO =
        "{\"entry\":[{\"changes\":[{\"field\":\"messages\",\"value\":{\"messages\":["
        + "{\"id\":\"wamid.AUD\",\"from\":\"5583999999999\",\"type\":\"audio\","
        + "\"audio\":{\"id\":\"media-123\",\"mime_type\":\"audio/ogg; codecs=opus\",\"voice\":true}}"
        + "]}}]}]}";

    private static final String PAYLOAD_IMAGEM =
        "{\"entry\":[{\"changes\":[{\"field\":\"messages\",\"value\":{\"messages\":["
        + "{\"id\":\"wamid.IMG\",\"from\":\"5583999999999\",\"type\":\"image\",\"image\":{\"id\":\"media-999\"}}"
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

    @Test
    void post_MensagemDeAudio_DeveDelegarParaOAudioServiceComOMediaId() throws Exception {
        when(signatureValidator.isConfigurado()).thenReturn(true);
        when(signatureValidator.assinaturaValida(any(), any())).thenReturn(true);
        when(idempotencyService.registrarSeNovo("WHATSAPP", "wamid.AUD")).thenReturn(true);

        mockMvc.perform(post("/webhook/whatsapp")
                        .with(user("webhook")).with(csrf())
                        .header("X-Hub-Signature-256", "sha256=qualquer")
                        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD_AUDIO))
                .andExpect(status().isOk());

        verify(audioService).processarAudio("5583999999999", "media-123");
        verify(agentService, never()).processarMensagem(any(), any());
    }

    /**
     * Reentrega da Meta não pode disparar download + transcrição de novo —
     * ambos são pagos. O dedup roda antes do roteamento por tipo.
     */
    @Test
    void post_AudioDuplicado_NaoDeveBaixarNemTranscreverDeNovo() throws Exception {
        when(signatureValidator.isConfigurado()).thenReturn(true);
        when(signatureValidator.assinaturaValida(any(), any())).thenReturn(true);
        when(idempotencyService.registrarSeNovo("WHATSAPP", "wamid.AUD")).thenReturn(false);

        mockMvc.perform(post("/webhook/whatsapp")
                        .with(user("webhook")).with(csrf())
                        .header("X-Hub-Signature-256", "sha256=qualquer")
                        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD_AUDIO))
                .andExpect(status().isOk());

        verify(audioService, never()).processarAudio(any(), any());
    }

    @Test
    void post_TipoNaoSuportado_DeveResponder200ESerIgnorado() throws Exception {
        when(signatureValidator.isConfigurado()).thenReturn(true);
        when(signatureValidator.assinaturaValida(any(), any())).thenReturn(true);

        mockMvc.perform(post("/webhook/whatsapp")
                        .with(user("webhook")).with(csrf())
                        .header("X-Hub-Signature-256", "sha256=qualquer")
                        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD_IMAGEM))
                .andExpect(status().isOk());

        verify(agentService, never()).processarMensagem(any(), any());
        verify(audioService, never()).processarAudio(any(), any());
    }
}
