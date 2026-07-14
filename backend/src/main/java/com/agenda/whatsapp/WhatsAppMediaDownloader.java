package com.agenda.whatsapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Baixa mídia recebida pelo webhook (hoje só áudio) da Graph API da Meta.
 * <p>
 * O webhook nunca traz o arquivo — só um ID de mídia. Pegar os bytes exige
 * DUAS chamadas, ambas autenticadas com o mesmo {@code WHATSAPP_TOKEN} usado
 * para enviar mensagens:
 * <ol>
 *   <li>{@code GET /{media-id}} → devolve uma URL temporária + mime type + tamanho;</li>
 *   <li>{@code GET <URL temporária>} → devolve os bytes.</li>
 * </ol>
 * A separação em dois métodos é proposital: o chamador consegue checar o
 * TAMANHO (passo 1) e desistir antes de baixar o arquivo e pagar a transcrição
 * — ver {@code WhatsAppAudioService}.
 * <p>
 * Diferente de {@link WhatsAppCloudApiService}, esta classe não tem
 * {@code @Profile}: o token é opcional ({@code :} no default) e, sem ele, os
 * métodos apenas falham com log — mesma política do classificador de intenção
 * quando a chave da IA não está configurada. Assim o bean existe em qualquer
 * perfil e ninguém precisa de injeção condicional.
 */
@Slf4j
@Service
public class WhatsAppMediaDownloader {

    @Value("${whatsapp.token:}")
    private String token;

    private static final String GRAPH_URL = "https://graph.facebook.com/v20.0/";
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 20_000;

    private final RestTemplate restTemplate = criarRestTemplate();

    /**
     * Metadados devolvidos pelo passo 1. {@code url} é temporária (expira em
     * poucos minutos) e só serve para o passo 2.
     */
    public record MetadadosMidia(String url, String mimeType, long tamanhoBytes) {}

    /**
     * Passo 1: consulta os metadados da mídia.
     *
     * @return os metadados, ou {@code null} se a chamada falhou (token ausente,
     *         mídia expirada, erro de rede) — o chamador decide o que responder.
     */
    public MetadadosMidia obterMetadados(String mediaId) {
        if (token == null || token.isBlank()) {
            log.error("[WHATSAPP-MEDIA] WHATSAPP_TOKEN não configurado — não é possível baixar mídia.");
            return null;
        }

        try {
            ResponseEntity<Map<String, Object>> resposta = restTemplate.exchange(
                    GRAPH_URL + mediaId, HttpMethod.GET,
                    new HttpEntity<>(headersComToken()),
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );

            Map<String, Object> corpo = resposta.getBody();
            if (corpo == null || corpo.get("url") == null) {
                log.error("[WHATSAPP-MEDIA] Resposta sem 'url' para a mídia {}", mediaId);
                return null;
            }

            Object tamanho = corpo.get("file_size");
            return new MetadadosMidia(
                    corpo.get("url").toString(),
                    corpo.get("mime_type") != null ? corpo.get("mime_type").toString() : "",
                    tamanho instanceof Number n ? n.longValue() : 0L
            );

        } catch (Exception e) {
            log.error("[WHATSAPP-MEDIA] Falha ao obter metadados da mídia {}: {}", mediaId, e.getMessage());
            return null;
        }
    }

    /**
     * Passo 2: baixa os bytes da URL temporária devolvida por
     * {@link #obterMetadados(String)}. A URL é da Meta mas continua exigindo o
     * header de autorização.
     *
     * @return os bytes, ou {@code null} se a chamada falhou.
     */
    public byte[] baixarConteudo(String url) {
        if (token == null || token.isBlank()) {
            log.error("[WHATSAPP-MEDIA] WHATSAPP_TOKEN não configurado — não é possível baixar mídia.");
            return null;
        }

        try {
            ResponseEntity<byte[]> resposta = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(headersComToken()),
                    byte[].class
            );
            return resposta.getBody();

        } catch (Exception e) {
            log.error("[WHATSAPP-MEDIA] Falha ao baixar conteúdo da mídia: {}", e.getMessage());
            return null;
        }
    }

    private HttpHeaders headersComToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    /**
     * Timeouts explícitos: sem eles, uma Graph API lenta prenderia
     * indefinidamente a thread do pool assíncrono que processa o áudio.
     */
    private static RestTemplate criarRestTemplate() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }
}
