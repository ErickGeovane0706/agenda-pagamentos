package com.agenda.whatsapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
public class WhatsAppCloudApiService implements WhatsAppService {

    @Value("${whatsapp.phone-number-id}")
    private String phoneNumberId;

    @Value("${whatsapp.token}")
    private String token;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void enviar(String telefoneDestino, String mensagem) {
        if (telefoneDestino == null || telefoneDestino.isBlank()) {
            log.warn("[WHATSAPP] Telefone de destino não informado, mensagem não enviada.");
            return;
        }

        try {
            String url = "https://graph.facebook.com/v20.0/" + phoneNumberId + "/messages";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            // Formata o número: remove +, espaços e traços
            String numero = telefoneDestino.replaceAll("[^0-9]", "");

            Map<String, Object> body = Map.of(
                    "messaging_product", "whatsapp",
                    "to", numero,
                    "type", "text",
                    "text", Map.of("body", mensagem)
            );

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            log.info("[WHATSAPP] Mensagem enviada para {} — status: {}", numero, response.getStatusCode());

        } catch (Exception e) {
            log.error("[WHATSAPP] Falha ao enviar mensagem para {}: {}", telefoneDestino, e.getMessage());
            // Não propagar — falha no WhatsApp não deve quebrar o scheduler
        }
    }
}