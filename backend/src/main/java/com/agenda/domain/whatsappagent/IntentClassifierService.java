package com.agenda.domain.whatsappagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    /**
     * Linha que separa a parte FIXA do prompt (instruções + exemplos, idêntica em
     * toda mensagem de todo cliente) da parte que muda a cada chamada.
     * <p>
     * A divisão existe por causa do cache de prompt da Anthropic, que é um
     * casamento de PREFIXO: qualquer byte que mude invalida tudo o que vem
     * depois. Enquanto {@code {{DATA_HOJE}}} e {@code {{FILTROS_ANTERIORES}}}
     * estavam no MEIO do arquivo, o trecho estável antes deles tinha 3.277
     * tokens — abaixo do mínimo cacheável do Haiku 4.5, que é 4.096. Ou seja:
     * o cache não pegava, e nem dava erro; falhava calado. Com as variáveis no
     * fim, o bloco fixo tem 6.825 tokens e passa a ser cacheável.
     * <p>
     * <b>Se você mover qualquer variável de volta para cima deste marcador, o
     * cache morre em silêncio</b> e o custo por mensagem quadruplica.
     */
    private static final String MARCADOR_CONTEXTO = "=== CONTEXTO DESTA MENSAGEM ===";

    private final RestTemplate restTemplate = criarRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Instruções + exemplos. Nunca muda — é o que vai para o cache. */
    private final String blocoEstavel;
    /** Data de hoje, filtros anteriores e a mensagem do cliente. Muda a cada chamada. */
    private final String blocoVolatil;

    public IntentClassifierService() {
        String template = carregarPromptTemplate();
        int corte = template.indexOf(MARCADOR_CONTEXTO);
        if (corte < 0) {
            throw new IllegalStateException(
                    "O prompt " + PROMPT_RESOURCE + " não tem o marcador \"" + MARCADOR_CONTEXTO
                            + "\", que separa o bloco cacheável do volátil.");
        }
        this.blocoEstavel = template.substring(0, corte).stripTrailing();
        this.blocoVolatil = template.substring(corte);
    }

    /**
     * Classifica a mensagem do cliente. Nunca lança exceção — qualquer
     * falha (rede, parsing) é tratada e devolve {@code naoEntendido()},
     * deixando o agente decidir a mensagem de erro amigável.
     */
    public ResultadoClassificacao classificar(String mensagemCliente, FiltrosAgente filtrosAnteriores) {
        if (mensagemCliente == null || mensagemCliente.isBlank()) {
            return ResultadoClassificacao.naoEntendido();
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.error("[WHATSAPP-AGENTE] ANTHROPIC_API_KEY não configurada — o agente conversacional não pode classificar mensagens. Configure a variável de ambiente no Railway.");
            return ResultadoClassificacao.naoEntendido();
        }

        try {
            String contexto = montarContexto(mensagemCliente, filtrosAnteriores);
            String respostaBruta = chamarApiAnthropic(contexto);
            return parsearResposta(respostaBruta);
        } catch (Exception e) {
            log.error("[WHATSAPP-AGENTE] Falha ao classificar mensagem '{}': {}", mensagemCliente, e.getMessage(), e);
            return ResultadoClassificacao.naoEntendido();
        }
    }

    /** Só o pedaço que muda a cada mensagem — o bloco estável vai separado, no cache. */
    private String montarContexto(String mensagemCliente, FiltrosAgente filtrosAnteriores) {
        String hoje = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        return blocoVolatil
                .replace("{{DATA_HOJE}}", hoje)
                .replace("{{FILTROS_ANTERIORES}}", formatarFiltrosAnteriores(filtrosAnteriores))
                .replace("{{MENSAGEM_CLIENTE}}", sanitizarParaPrompt(mensagemCliente));
    }

    /**
     * Representação compacta dos filtros da última consulta do usuário,
     * para o prompt dar contexto suficiente à LLM decidir entre
     * {@code REFINAR_ULTIMA_CONSULTA} e uma intenção nova. Nunca inclui
     * dados financeiros — só os próprios filtros de texto já extraídos
     * anteriormente da mensagem do cliente.
     */
    private String formatarFiltrosAnteriores(FiltrosAgente filtros) {
        if (filtros == null) {
            return "nenhuma consulta anterior nesta conversa";
        }
        StringBuilder descricao = new StringBuilder();
        if (filtros.getLoja() != null) descricao.append("loja=").append(filtros.getLoja()).append("; ");
        if (filtros.getTipoPagamento() != null) descricao.append("tipoPagamento=").append(filtros.getTipoPagamento()).append("; ");
        if (filtros.getPeriodo() != null) descricao.append("periodo=").append(filtros.getPeriodo()).append("; ");
        if (filtros.getFiltroStatus() != null) descricao.append("filtroStatus=").append(filtros.getFiltroStatus()).append("; ");
        return descricao.isEmpty() ? "nenhum filtro específico" : descricao.toString();
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

    /**
     * O bloco fixo vai no {@code system} com um {@code cache_control}, e só o
     * contexto da mensagem vai no turno do usuário. A ordem em que a API monta o
     * prompt é {@code tools → system → messages}, então marcar o fim do system
     * cacheia exatamente as instruções + exemplos — que são idênticos para todos
     * os clientes, já que o prompt não carrega nenhum dado de ninguém. Na prática
     * o cache é compartilhado por toda a base: a pergunta de um cliente esquenta
     * o cache para os outros.
     */
    private String chamarApiAnthropic(String contexto) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", MAX_TOKENS_RESPOSTA,
                "system", List.of(Map.of(
                        "type", "text",
                        "text", blocoEstavel,
                        "cache_control", Map.of("type", "ephemeral")
                )),
                "messages", List.of(
                        Map.of("role", "user", "content", contexto)
                )
        );

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    baseUrl, HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );

            Map<String, Object> corpo = response.getBody();
            logarUsoDeCache(corpo);
            return extrairTexto(corpo);
        } catch (RestClientException e) {
            throw new IllegalStateException("Erro ao chamar API da Anthropic: " + e.getMessage(), e);
        }
    }

    /**
     * Único jeito de saber, em produção, se o cache está realmente pegando: se
     * {@code cache_read} ficar sempre em zero, algum byte do bloco estável está
     * mudando entre as chamadas e o cache está sendo escrito e jogado fora.
     */
    private void logarUsoDeCache(Map<String, Object> resposta) {
        if (resposta != null && resposta.get("usage") instanceof Map<?, ?> uso) {
            log.info("[WHATSAPP-AGENTE] Cache do prompt — escrito: {} | lido: {} | sem cache: {}",
                    uso.get("cache_creation_input_tokens"),
                    uso.get("cache_read_input_tokens"),
                    uso.get("input_tokens"));
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
     * <p>
     * Visibilidade de pacote (não {@code private}) para ser exercitado
     * diretamente por {@link IntentClassifierServiceTest} sem precisar de
     * uma chamada HTTP real — é o caminho que estava sem cobertura quando
     * o formato do JSON do prompt mudou e quebrou silenciosamente.
     */
    ResultadoClassificacao parsearResposta(String respostaBruta) throws IOException {
        String json = respostaBruta
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();

        JsonNoLlmResponse parsed = objectMapper.readValue(json, JsonNoLlmResponse.class);
        return parsed.toResultado();
    }

    /**
     * RestTemplate com timeouts de conexão e leitura. Sem eles, uma resposta
     * lenta da Anthropic prenderia a thread indefinidamente — como
     * {@code processarMensagem} roda no pool async, um pico de lentidão
     * poderia esgotar o pool.
     */
    private static RestTemplate criarRestTemplate() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
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
     * <p>
     * {@code @JsonIgnoreProperties(ignoreUnknown = true)}: um campo extra
     * que a LLM eventualmente devolva (ex.: alucinação, ou o prompt ganhar
     * um campo novo antes deste DTO ser atualizado) nunca pode derrubar
     * TODA classificação — só o próprio campo desconhecido é ignorado.
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
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

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private static class FiltrosJson {
        public String loja;
        public String tipoPagamento;
        public String periodo;
        public String diaSemanaAlvo;
        public String posicaoQuinzena;
        public Integer diaDoMes;
        public String mesReferencia;
        public String dataInicio;
        public String dataFim;
        public String fornecedor;
        public String filtroStatus;

        FiltrosAgente toFiltrosAgente() {
            return FiltrosAgente.builder()
                    .loja(loja)
                    .tipoPagamento(parseEnumSeguro(tipoPagamento, TipoPagamentoAgente.class))
                    .periodo(parseEnumSeguro(periodo, TipoPeriodoAgente.class, TipoPeriodoAgente.SEM_FILTRO))
                    .diaSemanaAlvo(parseEnumSeguro(diaSemanaAlvo, DiaSemanaAgente.class))
                    .posicaoQuinzena(parseEnumSeguro(posicaoQuinzena, PosicaoQuinzenaAgente.class))
                    .diaDoMes(diaDoMes)
                    .mesReferencia(parseEnumSeguro(mesReferencia, MesReferenciaAgente.class))
                    .dataInicio(parseDataSegura(dataInicio))
                    .dataFim(parseDataSegura(dataFim))
                    .fornecedor(fornecedor)
                    .filtroStatus(parseEnumSeguro(filtroStatus, FiltroStatusAgente.class, FiltroStatusAgente.PENDENTE))
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