package com.agenda.domain.assinatura;

import com.agenda.domain.empresa.Empresa;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Assinatura de uma empresa (1 por empresa — unique em empresa_id).
 *
 * lojasContratadas é a "quantidade" do modelo de preço por loja.
 * vigenteAte NULL significa sem controle de vigência (trial/ativação manual).
 * Os campos gateway_* ficam NULL até a integração com o Asaas (Fase 2).
 */
@Entity
@Table(name = "assinaturas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Assinatura {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false, unique = true)
    private Empresa empresa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAssinatura status;

    @Column(name = "lojas_contratadas", nullable = false)
    private Integer lojasContratadas;

    @Column(name = "vigente_ate")
    private LocalDate vigenteAte;

    @Column(name = "gateway_customer_id")
    private String gatewayCustomerId;

    @Column(name = "gateway_subscription_id")
    private String gatewaySubscriptionId;

    @Column(name = "criado_em", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "atualizado_em", nullable = false)
    @Builder.Default
    private LocalDateTime atualizadoEm = LocalDateTime.now();

    @PreUpdate
    void aoAtualizar() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
