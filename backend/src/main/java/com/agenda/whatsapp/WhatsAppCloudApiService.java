package com.agenda.whatsapp;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Profile("!dev & !test")
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
    @Override
    public void enviarTemplate(String telefoneDestino, String nomeTemplate, List<String> parametros) {
        if (telefoneDestino == null || telefoneDestino.isBlank()) return;

        try {
            String url = "https://graph.facebook.com/v20.0/" + phoneNumberId + "/messages";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            String numero = telefoneDestino.replaceAll("[^0-9]", "");

            // Monta os componentes de parâmetro conforme a Meta exige
            List<Map<String, String>> params = new ArrayList<>();
            for (String valor : parametros) {
                params.add(Map.of("type", "text", "text", valor));
            }

            Map<String, Object> body = Map.of(
                    "messaging_product", "whatsapp",
                    "to", numero,
                    "type", "template",
                    "template", Map.of(
                            "name", nomeTemplate,
                            "language", Map.of("code", "pt_BR"),
                            "components", List.of(
                                    Map.of("type", "body", "parameters", params)
                            )
                    )
            );

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            log.info("[WHATSAPP] Template '{}' enviado para {} — status: {}",
                    nomeTemplate, numero, response.getStatusCode());

        } catch (Exception e) {
            log.error("[WHATSAPP] Falha ao enviar template para {}: {}", telefoneDestino, e.getMessage());
        }
    }
}