package com.agenda.domain.notificacao;

import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

/**
 * DTO de saída com as preferências de notificação do usuário.
 * Espelha os campos da entidade {@link PreferenciaNotificacao} sem expor
 * a relação JPA ou dados sensíveis do usuário.
 */
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
    /**
     * Converte a entidade JPA para o DTO de resposta.
     * {@code lojaIds} vem da {@code @ElementCollection} da entidade — é
     * carregada LAZY pelo Hibernate, então copiamos para um novo HashSet
     * aqui dentro (ainda em transação) para forçar a inicialização da
     * coleção antes que o objeto saia do escopo da sessão. Sem isso, o
     * Jackson tenta serializar o proxy fora da transação e lança
     * LazyInitializationException ("no Session").
     */
    public static PreferenciaNotificacaoDTO from(PreferenciaNotificacao p) {
        return new PreferenciaNotificacaoDTO(
                p.getId(),
                p.getTelefoneWhatsapp(),
                p.getWhatsappAtivo(),
                p.getHorario1(),
                p.getHorario2(),
                p.getHorario3(),
                p.getHorario4(),
                new java.util.HashSet<>(p.getLojaIds())
        );
    }
}