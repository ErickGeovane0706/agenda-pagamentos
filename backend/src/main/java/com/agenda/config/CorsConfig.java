package com.agenda.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * Configuração CORS global. Lê origens permitidas de
 * application.yml (cors.allowed-origins) e expõe o header
 * Location para que o frontend consiga ler redirects.
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        var source = corsConfigurationSource();
        return new CorsFilter(source);
    }

    /**
     * Configuração CORS para todos os endpoints /api/**.
     *
     * Regra de negócio: em desenvolvimento, permite origens locais
     * (localhost:5173 Vite, localhost:3000 React). Em produção, usa
     * a propriedade cors.allowed-origins (ex.: domínio do frontend).
     * Credenciais (cookies de refresh token) são permitidas.
     * O header Content-Disposition é exposto para download de arquivos.
     */
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        config.setAllowCredentials(true);

        var origins = allowedOrigins.isBlank()
            ? List.of("http://localhost:5173", "http://localhost:3000")
            : Arrays.asList(allowedOrigins.split(","));
        config.setAllowedOrigins(origins);

        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setExposedHeaders(Arrays.asList("Authorization", "Content-Disposition"));

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
