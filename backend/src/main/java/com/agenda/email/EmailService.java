package com.agenda.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Envio de email transacional via API HTTP do Resend.
 * <p>
 * Assíncrono e resiliente pelos mesmos motivos do WhatsApp: uma falha ou
 * lentidão do provedor de email não pode segurar a thread nem derrubar o fluxo
 * que pediu o envio (ex.: reset de senha responde neutro independentemente do
 * resultado do email). Timeouts curtos garantem que a thread assíncrona não
 * fique presa.
 * <p>
 * Sem {@code RESEND_API_KEY} configurada, apenas registra o email em log —
 * assim o app roda em dev e o link de reset aparece no console para testes.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    @Value("${resend.api-key:}")
    private String apiKey;

    @Value("${resend.from}")
    private String from;

    private final RestTemplate restTemplate = criarRestTemplate();

    private static RestTemplate criarRestTemplate() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    @Async
    public void enviar(String para, String assunto, String htmlCorpo) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[EMAIL] RESEND_API_KEY não configurada — email '{}' para {} NÃO enviado (dev).", assunto, para);
            return;
        }
        try {
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            var body = Map.of("from", from, "to", List.of(para), "subject", assunto, "html", htmlCorpo);

            var resposta = restTemplate.exchange("https://api.resend.com/emails",
                    HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            log.info("[EMAIL] '{}' enviado para {} — status {}", assunto, para, resposta.getStatusCode());
        } catch (Exception e) {
            log.error("[EMAIL] Falha ao enviar '{}' para {}: {}", assunto, para, e.getMessage());
        }
    }
}
