package com.agenda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

/**
 * Ponto de entrada da aplicação Spring Boot.
 * Fixa o timezone padrão da JVM para America/Sao_Paulo (BRT)
 * antes de qualquer inicialização — todos os vencimentos,
 * pagamentos e notificações usam horário brasileiro.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class AgendaApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        SpringApplication.run(AgendaApplication.class, args);
    }
}