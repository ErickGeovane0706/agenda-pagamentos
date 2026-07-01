package com.agenda.domain.whatsappagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Classifica a intenção de uma mensagem de WhatsApp chamando a API da
 * Anthropic (Claude Haiku — ver justificativa de custo/modelo na decisão
 * de produto: tarefa de classificação simples, alto volume, não precisa
 * de um modelo maior).
 * <p>
 * IMPORTANTE — fronteira de segurança: este serviço SÓ recebe o texto da
 * mensagem do cliente. Ele nunca recebe nem tem acesso a dados financeiros,
 * IDs de empresa, ou qualquer informação do banco. A LLM devolve apenas
 * {@link ResultadoClassificacao} (intenção + filtros de texto), que o
 * {@link WhatsAppAgentService} usa para consultar o banco — a busca real
 * e os valores retornados são 100% determinísticos, nunca gerados pela LLM.
 * <p>
 * Em caso de falha (timeout, erro HTTP, JSON malformado), devolve
 * {@link ResultadoClassificacao#naoEntendido()} em vez de propagar exceção,
 * para o agente sempre conseguir responder algo ao cliente.
 */
@Slf4j
@Service
public class IntentClassifierService {

    @Value("${anthropic.api-key}")
    private String apiKey;

    @Value("${anthropic.model:claude-haiku-4-5}")
    private String model;

    @Value("${anthropic.base-url:https://api.anthropic.com/v1/messages}")
    private String baseUrl;

    private static final String PROMPT_RESOURCE = "prompts/classificador-whatsapp.txt";
    private static final int MAX_TOKENS_RESPOSTA = 300;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String promptTemplate;

    public IntentClassifierService() {
        this.promptTemplate = carregarPromptTemplate();
    }

    /**
     * Classifica a mensagem do cliente. Nunca lança exceção — qualquer
     * falha (rede, parsing) é tratada e devolve {@code naoEntendido()},
     * deixando o agente decidir a mensagem de erro amigável.
     */
    public ResultadoClassificacao classificar(String mensagemCliente) {
        if (mensagemCliente == null || mensagemCliente.isBlank()) {
            return ResultadoClassificacao.naoEntendido();
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.error("[WHATSAPP-AGENTE] ANTHROPIC_API_KEY não configurada — o agente conversacional não pode classificar mensagens. Configure a variável de ambiente no Railway.");
            return ResultadoClassificacao.naoEntendido();
        }

        try {
            String prompt = montarPrompt(mensagemCliente);
            String respostaBruta = chamarApiAnthropic(prompt);
            return parsearResposta(respostaBruta);
        } catch (Exception e) {
            log.error("[WHATSAPP-AGENTE] Falha ao classificar mensagem '{}': {}", mensagemCliente, e.getMessage(), e);
            return ResultadoClassificacao.naoEntendido();
        }
    }

    private String montarPrompt(String mensagemCliente) {
        String hoje = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        return promptTemplate
                .replace("{{DATA_HOJE}}", hoje)
                .replace("{{MENSAGEM_CLIENTE}}", sanitizarParaPrompt(mensagemCliente));
    }

    /**
     * Remove aspas triplas do texto do cliente para que ele não consiga
     * "fechar" o bloco de citação do prompt e injetar instruções extras
     * (mitigação simples de prompt injection — a mensagem do cliente é
     * a única entrada não confiável que chega a este serviço).
     */
    private String sanitizarParaPrompt(String texto) {
        return texto.replace("\"\"\"", "'''").trim();
    }

    private String chamarApiAnthropic(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", MAX_TOKENS_RESPOSTA,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    baseUrl, HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );

            return extrairTexto(response.getBody());
        } catch (RestClientException e) {
            throw new IllegalStateException("Erro ao chamar API da Anthropic: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extrairTexto(Map<String, Object> respostaApi) {
        if (respostaApi == null) {
            throw new IllegalStateException("Resposta vazia da API da Anthropic");
        }
        List<Map<String, Object>> content = (List<Map<String, Object>>) respostaApi.get("content");
        if (content == null || content.isEmpty()) {
            throw new IllegalStateException("Resposta da Anthropic sem campo 'content'");
        }
        Object texto = content.get(0).get("text");
        if (texto == null) {
            throw new IllegalStateException("Bloco de conteúdo sem campo 'text'");
        }
        return texto.toString();
    }

    /**
     * Faz o parsing do JSON devolvido pela LLM. Tolera blocos ```json
     * que o modelo eventualmente envolva a resposta, mesmo com a
     * instrução explícita no prompt para não fazer isso.
     */
    private ResultadoClassificacao parsearResposta(String respostaBruta) throws IOException {
        String json = respostaBruta
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();

        JsonNoLlmResponse parsed = objectMapper.readValue(json, JsonNoLlmResponse.class);
        return parsed.toResultado();
    }

    private String carregarPromptTemplate() {
        try (InputStream is = new ClassPathResource(PROMPT_RESOURCE).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível carregar o prompt do classificador em " + PROMPT_RESOURCE, e);
        }
    }

    /**
     * Espelha exatamente o formato JSON pedido no prompt (campos em
     * snake/camel conforme instruído à LLM). Mantido como classe interna
     * privada porque é só um DTO de desserialização intermediário — o
     * resto do código trabalha com {@link ResultadoClassificacao}.
     */
    private static class JsonNoLlmResponse {
        public String intencao;
        public FiltrosJson filtros;

        ResultadoClassificacao toResultado() {
            IntencaoAgente intencaoEnum;
            try {
                intencaoEnum = IntencaoAgente.valueOf(intencao);
            } catch (Exception e) {
                intencaoEnum = IntencaoAgente.NAO_ENTENDIDO;
            }

            FiltrosAgente filtrosAgente = (filtros != null) ? filtros.toFiltrosAgente() : FiltrosAgente.builder()
                    .periodo(TipoPeriodoAgente.SEM_FILTRO)
                    .build();

            return ResultadoClassificacao.builder()
                    .intencao(intencaoEnum)
                    .filtros(filtrosAgente)
                    .build();
        }
    }

    private static class FiltrosJson {
        public String loja;
        public String tipoPagamento;
        public String periodo;
        public String dataInicio;
        public String dataFim;
        public String fornecedor;
        public Boolean incluirPagos;

        FiltrosAgente toFiltrosAgente() {
            return FiltrosAgente.builder()
                    .loja(loja)
                    .tipoPagamento(parseEnumSeguro(tipoPagamento, TipoPagamentoAgente.class))
                    .periodo(parseEnumSeguro(periodo, TipoPeriodoAgente.class, TipoPeriodoAgente.SEM_FILTRO))
                    .dataInicio(parseDataSegura(dataInicio))
                    .dataFim(parseDataSegura(dataFim))
                    .fornecedor(fornecedor)
                    .incluirPagos(Boolean.TRUE.equals(incluirPagos))
                    .build();
        }

        private <T extends Enum<T>> T parseEnumSeguro(String valor, Class<T> tipo) {
            return parseEnumSeguro(valor, tipo, null);
        }

        private <T extends Enum<T>> T parseEnumSeguro(String valor, Class<T> tipo, T valorPadrao) {
            if (valor == null || valor.isBlank() || "null".equalsIgnoreCase(valor)) {
                return valorPadrao;
            }
            try {
                return Enum.valueOf(tipo, valor.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return valorPadrao;
            }
        }

        private LocalDate parseDataSegura(String valor) {
            if (valor == null || valor.isBlank() || "null".equalsIgnoreCase(valor)) {
                return null;
            }
            try {
                return LocalDate.parse(valor.trim());
            } catch (Exception e) {
                return null;
            }
        }
    }
}