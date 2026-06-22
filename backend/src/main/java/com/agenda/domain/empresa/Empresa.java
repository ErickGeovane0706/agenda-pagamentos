package com.agenda.domain.empresa;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade raiz do sistema. Cada empresa é um tenant isolado que
 * possui seus próprios usuários, lojas, bancos e pagamentos.
 * O campo solicitouExclusao inicia o fluxo de exclusão soft-delete.
 */
@Entity
@Table(name = "empresas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Empresa {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "solicitou_exclusao")
    @Builder.Default
    private Boolean solicitouExclusao = false;

    @Column(name = "solicitou_exclusao_em")
    private LocalDateTime solicitouExclusaoEm;

    @Column(name = "excluido_em")
    private LocalDateTime excluidoEm;
}
