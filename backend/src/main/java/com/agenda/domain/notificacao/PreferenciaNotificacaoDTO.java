package com.agenda.domain.notificacao;

import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

public record PreferenciaNotificacaoDTO(
        UUID id,
        String telefoneWhatsapp,
        boolean whatsappAtivo,
        LocalTime horario1,
        LocalTime horario2,
        LocalTime horario3,
        LocalTime horario4,
        Set<UUID> lojaIds
) {
    public static PreferenciaNotificacaoDTO from(PreferenciaNotificacao p) {
        return new PreferenciaNotificacaoDTO(
                p.getId(),
                p.getTelefoneWhatsapp(),
                p.getWhatsappAtivo(),
                p.getHorario1(),
                p.getHorario2(),
                p.getHorario3(),
                p.getHorario4(),
                p.getLojaIds()
        );
    }
}