package com.agenda.domain.coluna;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "valores_extras")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ValorExtra {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "coluna_id", nullable = false)
    private UUID colunaId;

    @Column(name = "registro_id", nullable = false)
    private UUID registroId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pagamento", nullable = false, length = 20)
    private TipoPagamento tipoPagamento;

    @Column(columnDefinition = "TEXT")
    private String valor;
}