package com.agenda.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting por chave arbitrária (Bucket4j, token bucket, em memória).
 * <p>
 * Usado para limites keados por IDENTIDADE — não por IP. Isso é intencional:
 * <ul>
 *   <li>Uma chave de identidade (email no login, telefone no webhook) NÃO é
 *       falsificável como o {@code X-Forwarded-For} — o atacante não consegue
 *       trocar de chave a cada requisição para escapar do teto.</li>
 *   <li>Não há risco de colapsar todos os usuários num único bucket (o que
 *       aconteceria com IP mal extraído atrás de proxy) e derrubar o acesso
 *       de todo mundo.</li>
 * </ul>
 * Limitação conhecida: os buckets vivem na memória da instância. Com mais de
 * uma instância no Railway, o teto real é multiplicado pelo nº de instâncias;
 * um restart zera as contagens. É suficiente como controle de ABUSO na
 * aplicação — não substitui proteção de borda (WAF/CDN) contra DDoS
 * volumétrico, que atua antes do tráfego chegar aqui.
 */
@Service
public class RateLimiterService {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Consome 1 token do bucket da {@code chave} (criando-o sob demanda).
     * Retorna {@code true} se havia token (requisição permitida), {@code false}
     * se o teto estourou. Use quando cada chamada deve custar um token
     * (ex.: cada mensagem do webhook, que dispara uma chamada paga de IA).
     */
    public boolean tentarConsumir(String chave, int capacidade, Duration janela) {
        return bucket(chave, capacidade, janela).tryConsume(1);
    }

    /**
     * Verifica se ainda há token disponível SEM consumir. Use para checar o
     * teto antes de uma operação cujo token só deve ser gasto em certos casos
     * (ex.: login — só uma tentativa que FALHA consome um token, para nunca
     * bloquear quem digitou a senha certa).
     */
    public boolean temTokenDisponivel(String chave, int capacidade, Duration janela) {
        return bucket(chave, capacidade, janela).getAvailableTokens() > 0;
    }

    private Bucket bucket(String chave, int capacidade, Duration janela) {
        return buckets.computeIfAbsent(chave, k -> {
            var limite = Bandwidth.classic(capacidade, Refill.greedy(capacidade, janela));
            return Bucket.builder().addLimit(limite).build();
        });
    }
}
