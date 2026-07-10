package com.agenda.domain.webhook;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Controle de idempotência de webhooks: garante que cada evento externo
 * (identificado por {@code origem} + {@code eventoId}) seja processado
 * uma única vez, mesmo com reentregas.
 */
@Service
@RequiredArgsConstructor
public class WebhookIdempotencyService {

    private final WebhookEventoProcessadoRepository repository;

    /**
     * Registra o evento e retorna {@code true} se é a primeira vez
     * (o chamador deve processar). Retorna {@code false} se já havia sido
     * processado (duplicata — ignorar).
     *
     * Caso comum de reentrega (mesma mensagem chega segundos depois, já
     * commitada): o {@code exists} resolve. No caso raro de duas entregas
     * quase simultâneas, a unique constraint {@code (origem, evento_id)}
     * é o backstop — a segunda falha no commit, a exceção sobe e o chamador
     * (que responde 200 mesmo em erro) não duplica o processamento.
     */
    @Transactional
    public boolean registrarSeNovo(String origem, String eventoId) {
        if (repository.existsByOrigemAndEventoId(origem, eventoId)) {
            return false;
        }
        repository.save(WebhookEventoProcessado.builder()
                .origem(origem)
                .eventoId(eventoId)
                .build());
        return true;
    }
}
