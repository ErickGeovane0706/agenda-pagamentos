package com.agenda.domain.webhook;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Registro de evento de webhook já processado, para idempotência.
 *
 * A chave lógica é o par {@code (origem, evento_id)} com unique constraint:
 * uma reentrega do mesmo evento (a Meta reenvia em timeout; o Stripe também)
 * é detectada e ignorada, evitando processamento duplicado.
 *
 * Genérica de propósito (coluna {@code origem}) para ser reaproveitada pelo
 * webhook do Stripe sem nova infraestrutura.
 */
@Entity
@Table(name = "webhook_evento_processado")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WebhookEventoProcessado {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String origem;

    @Column(name = "evento_id", nullable = false)
    private String eventoId;

    @Column(name = "processado_em", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime processadoEm = LocalDateTime.now();
}
