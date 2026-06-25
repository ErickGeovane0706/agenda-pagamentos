package com.agenda.domain.notificacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório para ler/gravar preferências de notificação por WhatsApp.
 * A query {@link #findAtivosComHorario(LocalTime)} é o coração do scheduler:
 * precisa retornar rápido porque é executada a cada minuto.
 */
public interface PreferenciaNotificacaoRepository extends JpaRepository<PreferenciaNotificacao, UUID> {

    /**
     * Retorna a preferência de um usuário específico, ou vazio se ele nunca
     * configurou. Usado no serviço para criar sob demanda se não existir.
     */
    Optional<PreferenciaNotificacao> findByUsuarioId(UUID usuarioId);

    /**
     * Busca todas as preferências ativas em que o horário informado bate com
     * QUALQUER um dos 4 horários configurados pelo usuário.
     * Usado pelo scheduler a cada execução (ex: a cada 15 minutos).
     */
    @Query("""
    SELECT DISTINCT p FROM PreferenciaNotificacao p
    JOIN FETCH p.usuario u
    JOIN FETCH u.empresa
    LEFT JOIN FETCH p.lojaIds
    WHERE p.whatsappAtivo = true
      AND p.telefoneWhatsapp IS NOT NULL
      AND (p.horario1 = :agora OR p.horario2 = :agora OR p.horario3 = :agora OR p.horario4 = :agora)
    """)
    List<PreferenciaNotificacao> findAtivosComHorario(@Param("agora") LocalTime agora);
}