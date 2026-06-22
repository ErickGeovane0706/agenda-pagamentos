package com.agenda.domain.notificacao;

import jakarta.validation.constraints.Pattern;

import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

/**
 * DTO de entrada para o usuário atualizar suas preferências de notificação.
 * <p>
 * Valida o telefone com DDI + DDD (padrão brasileiro internacional) e aceita
 * até 4 horários de disparo. {@code lojaIds} vazio = nenhuma loja notificada.
 */
public record AtualizarPreferenciaNotificacaoRequest(
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Telefone inválido. Use formato com DDI e DDD, ex: 5583999999999")
        String telefoneWhatsapp,

        boolean whatsappAtivo,

        LocalTime horario1,
        LocalTime horario2,
        LocalTime horario3,
        LocalTime horario4,

        Set<UUID> lojaIds
) {}