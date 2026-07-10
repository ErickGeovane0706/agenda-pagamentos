package com.agenda.domain.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WebhookEventoProcessadoRepository extends JpaRepository<WebhookEventoProcessado, UUID> {
    boolean existsByOrigemAndEventoId(String origem, String eventoId);
}
