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
     * Quantos telefones da empresa estão recebendo lembrete. Usado para aplicar
     * o teto de destinatários — cada um a mais é um template pago por disparo.
     */
    @Query("""
    SELECT COUNT(p) FROM PreferenciaNotificacao p
    WHERE p.usuario.empresa.id = :empresaId
      AND p.whatsappAtivo = true
    """)
    long contarAtivosDaEmpresa(@Param("empresaId") UUID empresaId);

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

    /**
     * Busca a preferência (e, por consequência, o usuário/empresa) pelo
     * número de telefone que mandou uma mensagem no webhook do WhatsApp.
     * <p>
     * Usado pelo agente conversacional para identificar quem está
     * perguntando ANTES de tocar em qualquer dado financeiro — ver
     * {@link com.agenda.domain.whatsappagent.WhatsAppAgentService}.
     * Só considera registros com {@code whatsappAtivo = true}: um usuário
     * que desativou a notificação não deve conseguir consultar dados pelo
     * número antigo.
     * <p>
     * Comparação normalizada (somente dígitos) porque o número pode chegar
     * da Meta com ou sem o prefixo "+" dependendo do país/formatação salva
     * no cadastro — a normalização usa {@code REGEXP_REPLACE} no próprio
     * SQL, espelhando o mesmo {@code replaceAll("[^0-9]", "")} que o
     * {@link com.agenda.domain.whatsappagent.WhatsAppAgentService} aplica
     * ao telefone recebido do webhook antes de consultar esta query.
     */
    @Query("""
    SELECT p FROM PreferenciaNotificacao p
    JOIN FETCH p.usuario u
    JOIN FETCH u.empresa
    LEFT JOIN FETCH p.lojaIds
    WHERE p.whatsappAtivo = true
      AND p.telefoneWhatsapp IS NOT NULL
      AND function('regexp_replace', p.telefoneWhatsapp, '[^0-9]', '', 'g') = :telefoneNormalizado
    """)
    Optional<PreferenciaNotificacao> findByTelefoneNormalizado(@Param("telefoneNormalizado") String telefoneNormalizado);

    /**
     * Mesma busca que {@link #findByTelefoneNormalizado(String)}, mas aceita
     * uma lista de variantes do telefone — necessário porque a Meta Cloud
     * API pode enviar o número do remetente COM ou SEM o nono dígito dos
     * celulares brasileiros, independente de como o número foi cadastrado
     * em Configurações. Ver
     * {@code WhatsAppAgentService.gerarVariantesTelefoneBr}, que monta a
     * lista de variantes antes de chamar este método.
     * <p>
     * {@code SELECT DISTINCT} apenas evita duplicar a mesma linha caso ela
     * coincidentemente bata com mais de uma variante da lista — não resolve
     * ambiguidade de cadastro duplicado (dois registros para o mesmo
     * titular), que continua sendo tratada no service.
     */
    @Query("""
    SELECT DISTINCT p FROM PreferenciaNotificacao p
    JOIN FETCH p.usuario u
    JOIN FETCH u.empresa
    LEFT JOIN FETCH p.lojaIds
    WHERE p.whatsappAtivo = true
      AND p.telefoneWhatsapp IS NOT NULL
      AND function('regexp_replace', p.telefoneWhatsapp, '[^0-9]', '', 'g') IN :telefonesNormalizados
    """)
    List<PreferenciaNotificacao> findByTelefoneNormalizadoIn(@Param("telefonesNormalizados") List<String> telefonesNormalizados);
}