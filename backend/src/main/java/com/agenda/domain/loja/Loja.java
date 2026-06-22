package com.agenda.domain.loja;

import com.agenda.domain.empresa.Empresa;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Filial / estabelecimento da empresa. Cada loja tem nome, CNPJ,
 * uma cor para identificação visual e pode ser desativada sem
 * perder o histórico de pagamentos associado.
 */
@Entity
@Table(name = "lojas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Loja {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(length = 18)
    private String cnpj;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(nullable = false, length = 7)
    @Builder.Default
    private String cor = "#3B82F6";

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();
}
