package com.agenda.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TEMPORÁRIO — remover assim que o Sentry for confirmado em produção.
 *
 * Dispara um erro de propósito para provar, de ponta a ponta, que o SDK do
 * backend está enviando eventos ao Sentry. O backend é bem defendido e não
 * produz 500 por entrada externa, então não havia como gerar um erro real de
 * fora sem isto.
 *
 * Fica sob /webhook para cair no anyRequest().permitAll() do SecurityConfig
 * (não precisa de JWT), mas exige uma chave secreta na query: sem ela, o
 * endpoint finge não existir (404). Assim ninguém que descubra a URL consegue
 * poluir o painel de erro.
 *
 * A chave vem de SENTRY_TESTE_KEY. Sem a variável, o endpoint responde 404
 * sempre — é o que garante que, esquecido em produção, ele não faça nada.
 */
@RestController
public class SentryTesteController {

    private static final Logger log = LoggerFactory.getLogger(SentryTesteController.class);

    @Value("${sentry-teste.key:}")
    private String chaveEsperada;

    @GetMapping("/webhook/sentry-teste")
    public ResponseEntity<String> dispararErro(@RequestParam(required = false) String chave) {
        if (chaveEsperada == null || chaveEsperada.isBlank() || !chaveEsperada.equals(chave)) {
            return ResponseEntity.notFound().build();
        }
        // log.error é o que o appender do Sentry captura (minimum-event-level=error).
        var erro = new RuntimeException("Teste de integracao do Sentry em producao — pode ignorar.");
        log.error("[SENTRY-TESTE] Disparo manual para validar o envio ao Sentry.", erro);
        return ResponseEntity.ok("Erro de teste disparado. Confira o painel do Sentry.");
    }
}
