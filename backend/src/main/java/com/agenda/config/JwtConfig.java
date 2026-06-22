package com.agenda.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configurações JWT injetadas do application.yml (prefixo "jwt").
 *
 * Regras de negócio:
 * - refreshExpiration padrão = 604800000ms (7 dias) — período dentro do
 *   qual o usuário pode renovar o access token sem reautenticar.
 * - Secret deve ser uma chave HMAC-SHA256 com no mínimo 256 bits.
 * - Expiration define o tempo de vida do access token (minutos).
 */
@Configuration
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtConfig {

    private String secret;
    private long expiration;
    private long refreshExpiration = 604_800_000;
}
