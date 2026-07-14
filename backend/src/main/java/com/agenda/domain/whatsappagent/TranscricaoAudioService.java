package com.agenda.domain.whatsappagent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Transcreve o áudio de uma mensagem de voz do WhatsApp usando a API de
 * transcrição da OpenAI (Whisper).
 * <p>
 * Por que um segundo fornecedor de IA: os modelos Claude — usados no
 * {@link IntentClassifierService} — não aceitam áudio como entrada, só texto,
 * imagem e documento. A transcrição é, portanto, uma etapa obrigatória ANTES
 * do classificador; daí para baixo o fluxo do agente é exatamente o mesmo de
 * uma mensagem digitada.
 * <p>
 * Não transcodifica nada: o WhatsApp manda nota de voz em OGG/Opus, que o
 * Whisper aceita direto (não precisa de ffmpeg no container).
 * <p>
 * Fronteira de segurança idêntica à do classificador: este serviço só recebe
 * os bytes do áudio do cliente e devolve texto. Nunca vê dado financeiro. O
 * texto transcrito entra no mesmo prompt do classificador e passa pela mesma
 * sanitização de prompt injection ({@code sanitizarParaPrompt}).
 * <p>
 * Nunca lança exceção: qualquer falha (chave ausente, timeout, formato
 * recusado) devolve {@code null}, e o chamador responde algo amigável ao
 * cliente em vez de deixá-lo no vácuo.
 */
@Slf4j
@Service
public class TranscricaoAudioService {

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.transcription-model:whisper-1}")
    private String model;

    @Value("${openai.transcription-url:https://api.openai.com/v1/audio/transcriptions}")
    private String baseUrl;

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    /** Transcrição é bem mais lenta que uma classificação — read timeout generoso. */
    private static final int READ_TIMEOUT_MS = 60_000;

    private final RestTemplate restTemplate = criarRestTemplate();

    /**
     * @param audio    bytes do arquivo, como vieram da Meta
     * @param mimeType mime type informado pela Meta (ex.: "audio/ogg; codecs=opus")
     * @return o texto transcrito, ou {@code null} se a transcrição falhou
     */
    public String transcrever(byte[] audio, String mimeType) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("[TRANSCRICAO] OPENAI_API_KEY não configurada — mensagens de áudio não podem ser transcritas. "
                    + "Configure a variável de ambiente no Railway.");
            return null;
        }
        if (audio == null || audio.length == 0) {
            return null;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(apiKey);

            MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
            // A OpenAI decide o decoder pela EXTENSÃO do nome do arquivo, não pelo
            // mime type — por isso o ByteArrayResource anônimo com getFilename().
            corpo.add("file", new ByteArrayResource(audio) {
                @Override
                public String getFilename() {
                    return "audio" + extensaoPara(mimeType);
                }
            });
            corpo.add("model", model);
            corpo.add("language", "pt");
            corpo.add("response_format", "json");

            ResponseEntity<Map<String, Object>> resposta = restTemplate.exchange(
                    baseUrl, HttpMethod.POST,
                    new HttpEntity<>(corpo, headers),
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );

            Map<String, Object> body = resposta.getBody();
            Object texto = body != null ? body.get("text") : null;
            if (texto == null || texto.toString().isBlank()) {
                log.warn("[TRANSCRICAO] Resposta da OpenAI sem texto utilizável.");
                return null;
            }
            return texto.toString().trim();

        } catch (Exception e) {
            log.error("[TRANSCRICAO] Falha ao transcrever áudio ({} bytes, {}): {}",
                    audio.length, mimeType, e.getMessage());
            return null;
        }
    }

    /**
     * Traduz o mime type da Meta para uma extensão que o Whisper reconheça.
     * Nota de voz do WhatsApp é sempre OGG/Opus na prática — os outros casos
     * cobrem um arquivo de áudio anexado em vez de gravado, e o default cai em
     * .ogg justamente por ser o caso real.
     */
    private String extensaoPara(String mimeType) {
        String tipo = mimeType != null ? mimeType.toLowerCase() : "";
        if (tipo.contains("mpeg") || tipo.contains("mp3")) return ".mp3";
        if (tipo.contains("mp4") || tipo.contains("m4a") || tipo.contains("aac")) return ".m4a";
        if (tipo.contains("wav")) return ".wav";
        if (tipo.contains("webm")) return ".webm";
        return ".ogg";
    }

    /**
     * Timeouts explícitos: sem eles, uma OpenAI lenta prenderia indefinidamente
     * a thread do pool assíncrono — um pico de lentidão poderia esgotá-lo.
     */
    private static RestTemplate criarRestTemplate() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }
}
