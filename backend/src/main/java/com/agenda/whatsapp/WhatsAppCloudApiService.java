package com.agenda.whatsapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Profile("!dev & !test")
@Service
public class WhatsAppCloudApiService implements WhatsAppService {

    @Value("${whatsapp.phone-number-id}")
    private String phoneNumberId;

    @Value("${whatsapp.token}")
    private String token;

    @Value("${whatsapp.template-language:pt_BR}")
    private String templateLanguage;

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    private final RestTemplate restTemplate = criarRestTemplate();

    /**
     * RestTemplate com timeouts. Sem eles, a Meta lenta prenderia a thread
     * (do scheduler ou do agente) indefinidamente. Envio de mensagem é rápido,
     * então read timeout curto.
     */
    private static RestTemplate criarRestTemplate() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    @Override
    public void enviarTemplate(String telefoneDestino, String nomeTemplate, List<String> parametros) {
        if (telefoneDestino == null || telefoneDestino.isBlank()) {
            log.warn("[WHATSAPP] Telefone de destino não informado, template '{}' não enviado.", nomeTemplate);
            return;
        }

        try {
            String url = "https://graph.facebook.com/v20.0/" + phoneNumberId + "/messages";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            // Formata o número: remove +, espaços e traços
            String numero = telefoneDestino.replaceAll("[^0-9]", "");

            // Parâmetros posicionais {{1}}, {{2}}, {{3}}... na ordem da lista.
            // A Meta exige um objeto {"type": "text", "text": "..."} por parâmetro.
            List<Map<String, String>> parametrosBody = IntStream.range(0, parametros.size())
                    .mapToObj(i -> Map.of("type", "text", "text", sanitizar(parametros.get(i))))
                    .collect(Collectors.toList());

            Map<String, Object> template = Map.of(
                    "name", nomeTemplate,
                    "language", Map.of("code", templateLanguage),
                    "components", List.of(
                            Map.of(
                                    "type", "body",
                                    "parameters", parametrosBody
                            )
                    )
            );

            Map<String, Object> body = Map.of(
                    "messaging_product", "whatsapp",
                    "to", numero,
                    "type", "template",
                    "template", template
            );

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            log.info("[WHATSAPP] Template '{}' enviado para {} — status: {}", nomeTemplate, numero, response.getStatusCode());

        } catch (Exception e) {
            log.error("[WHATSAPP] Falha ao enviar template '{}' para {}: {}", nomeTemplate, telefoneDestino, e.getMessage());
            // Não propagar — falha no WhatsApp não deve quebrar o scheduler
        }
    }

    @Override
    public void enviarMensagemTexto(String telefoneDestino, String texto) {
        if (telefoneDestino == null || telefoneDestino.isBlank()) {
            log.warn("[WHATSAPP] Telefone de destino não informado, mensagem de texto não enviada.");
            return;
        }
        if (texto == null || texto.isBlank()) {
            log.warn("[WHATSAPP] Texto vazio, mensagem não enviada para {}.", telefoneDestino);
            return;
        }

        try {
            String url = "https://graph.facebook.com/v20.0/" + phoneNumberId + "/messages";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            String numero = telefoneDestino.replaceAll("[^0-9]", "");

            Map<String, Object> body = Map.of(
                    "messaging_product", "whatsapp",
                    "to", numero,
                    "type", "text",
                    "text", Map.of("body", texto, "preview_url", false)
            );

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            log.info("[WHATSAPP] Mensagem de texto enviada para {} — status: {}", numero, response.getStatusCode());

        } catch (Exception e) {
            log.error("[WHATSAPP] Falha ao enviar mensagem de texto para {}: {}", telefoneDestino, e.getMessage());
            // Não propagar — mesma política de enviarTemplate: erro de WhatsApp não derruba o agente
        }
    }

    /**
     * A Meta rejeita parâmetros com quebra de linha, tabulação ou mais de
     * 4 espaços consecutivos. Sanitiza aqui como última garantia, mesmo que
     * quem monta os valores (MensagemNotificacaoBuilder) já evite isso —
     * uma falha de validação aqui derrubaria a chamada à API inteira.
     */
    private String sanitizar(String valor) {
        if (valor == null) {
            return "";
        }
        return valor
                .replaceAll("[\\n\\r\\t]+", " ")
                .replaceAll(" {5,}", "    ")
                .trim();
    }
}