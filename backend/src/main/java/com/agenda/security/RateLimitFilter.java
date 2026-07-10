package com.agenda.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filtro de rate limiting por IP usando Bucket4j (token bucket).
 * Limite: 30 requisições a cada 15 minutos por IP.
 * Retorna 429 (Too Many Requests) quando o bucket esgota.
 */
@Component
@Order(1)
public class RateLimitFilter implements Filter {

    /**
     * Cache de buckets por IP. Bucket4j com algoritmo token bucket:
     * 30 tokens, reabastecimento de 30 tokens a cada 15 minutos (greedy).
     * Isso permite até 30 tentativas de login por IP a cada 15 minutos.
     */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private Bucket createBucket() {
        var limit = Bandwidth.classic(30, Refill.greedy(30, Duration.ofMinutes(15)));
        return Bucket.builder().addLimit(limit).build();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        var httpRequest = (HttpServletRequest) request;
        var path = httpRequest.getRequestURI();

        // Rate limiting por IP nas rotas públicas de autenticação (login + refresh).
        // É uma camada SECUNDÁRIA: o controle principal (infalsificável) é por
        // email no AuthService. Atrás do nginx/borda do Railway o X-Forwarded-For
        // pode ser parcialmente forjável — por isso não confiamos só nisto.
        if (!path.equals("/api/auth/login") && !path.equals("/api/auth/refresh")) {
            chain.doFilter(request, response);
            return;
        }

        // Extrai o IP real considerando proxy reverso (X-Forwarded-For)
        var ip = httpRequest.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = httpRequest.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }
        var bucket = buckets.computeIfAbsent(ip, k -> createBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            var httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Muitas tentativas. Tente novamente em 15 minutos.\"}");
        }
    }
}
