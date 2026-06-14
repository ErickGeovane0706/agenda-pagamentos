package com.agenda.domain.coluna;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "colunas_extras")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ColunaExtra {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pagamento", nullable = false, length = 20)
    private TipoPagamento tipoPagamento;

    @Column(nullable = false, length = 100)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_dado", nullable = false, length = 20)
    @Builder.Default
    private TipoDado tipoDado = TipoDado.TEXTO;

    @Column(nullable = false)
    @Builder.Default
    private Boolean obrigatorio = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer ordem = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;
}