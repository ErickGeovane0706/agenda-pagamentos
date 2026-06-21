package com.agenda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class AgendaApplication {

    public static void main(String[] args) {
        // O Railway (e a maioria dos containers) usa UTC por padrão. Como o
        // negócio inteiro (vencimentos, horários de notificação, etc) opera
        // em horário de Brasília, fixamos o timezone da JVM aqui, antes de
        // qualquer LocalDate/LocalTime.now() ser chamado em qualquer lugar
        // do sistema — isso evita ter que converter fuso manualmente em
        // cada service.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        SpringApplication.run(AgendaApplication.class, args);
    }
}