package com.agenda.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Verificação do Cloudflare Turnstile (anti-bot do cadastro público).
 * <p>
 * <b>Escape hatch deliberado:</b> sem {@code turnstile.secret} configurado, a
 * validação passa com aviso no log — mesmo padrão do
 * {@code WhatsAppSignatureValidator}. Isso permite desenvolver, testar e rodar
 * localmente antes de existir domínio próprio (a Cloudflare recusa hostname em
 * domínio compartilhado como {@code up.railway.app}). Quando a secret entrar no
 * ambiente, a proteção passa a valer sozinha, sem tocar em código.
 * <p>
 * <b>Falha fechada em erro de rede.</b> Se a Cloudflare não responder, o token é
 * recusado. É o oposto da política do {@code EmailService}, e de propósito: um
 * email que não sai é um cadastro perdido; um anti-bot que "passa" durante uma
 * instabilidade é a porta escancarada exatamente quando ninguém está olhando.
 * <p>
 * Para testes existem chaves fixas publicadas pela Cloudflare — a secret
 * {@code 1x0000000000000000000000000000000AA} sempre valida e a
 * {@code 2x0000000000000000000000000000000AA} sempre recusa.
 */
@Service
public class TurnstileValidator {

    private static final Logger log = LoggerFactory.getLogger(TurnstileValidator.class);

    private static final String URL_VERIFY = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    @Value("${turnstile.secret:}")
    private String secret;

    private final RestTemplate restTemplate = criarRestTemplate();

    /**
     * Timeouts curtos: esta chamada acontece DENTRO do POST de cadastro, com o
     * visitante esperando na tela. Uma Cloudflare lenta não pode transformar o
     * cadastro numa tela travada.
     */
    private static RestTemplate criarRestTemplate() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    /** Há secret configurada? Falso em dev, e aí {@link #valido} sempre passa. */
    public boolean isConfigurado() {
        return secret != null && !secret.isBlank();
    }

    /**
     * O token do widget é válido?
     *
     * @param token o {@code cf-turnstile-response} enviado pelo formulário
     * @param ip    IP de origem, opcional — a Cloudflare o usa como sinal extra
     */
    @SuppressWarnings("unchecked")
    public boolean valido(String token, String ip) {
        if (!isConfigurado()) {
            log.warn("[TURNSTILE] turnstile.secret não configurado — validação PULADA. "
                    + "Configure a secret para fechar o cadastro público.");
            return true;
        }
        if (token == null || token.isBlank()) {
            log.info("[TURNSTILE] Requisição sem token — recusada.");
            return false;
        }

        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("secret", secret);
            form.add("response", token);
            if (ip != null && !ip.isBlank()) {
                form.add("remoteip", ip);
            }

            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            var resposta = restTemplate.exchange(URL_VERIFY, HttpMethod.POST,
                    new HttpEntity<>(form, headers), Map.class);

            var corpo = resposta.getBody();
            var sucesso = corpo != null && Boolean.TRUE.equals(corpo.get("success"));
            if (!sucesso) {
                log.info("[TURNSTILE] Token recusado pela Cloudflare: {}",
                        corpo == null ? "sem corpo" : corpo.get("error-codes"));
            }
            return sucesso;

        } catch (Exception e) {
            log.error("[TURNSTILE] Falha ao verificar token: {} — recusando por segurança.", e.getMessage());
            return false;
        }
    }
}
