package com.agenda.domain.produto;

import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.loja.Loja;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade JPA de um produto em estoque, do módulo de Estoque/PDV.
 * <p>
 * Pertence a uma <b>loja</b>, não só à empresa: o cliente que pediu o módulo
 * vende ovos numa loja e gelo na outra, e um relatório de lucro do gelo com ovo
 * dentro não teria significado.
 * <p>
 * {@code quantidade} é {@code NUMERIC(15,3)} e nunca {@code double}: gelo se
 * vende por quilo e ovo por dúzia, e ponto flutuante binário não representa 0,1
 * exatamente — erro de arredondamento em dinheiro é o bug que ninguém encontra.
 * <p>
 * Produto <b>não se apaga, desativa</b> ({@code ativo = false}): produto com
 * venda registrada nunca pode sumir, senão o relatório perde a referência e a FK
 * estoura. Ver {@code docs/adr/0009-produto-desativa-nao-apaga.md}.
 */
@Entity
@Table(name = "produtos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Produto {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loja_id", nullable = false)
    private Loja loja;

    @Column(nullable = false, length = 300)
    private String nome;

    @Column(nullable = false, precision = 15, scale = 3)
    @Builder.Default
    private BigDecimal quantidade = BigDecimal.ZERO;

    @Column(name = "preco_custo", nullable = false, precision = 15, scale = 2)
    private BigDecimal precoCusto;

    @Column(name = "preco_venda", nullable = false, precision = 15, scale = 2)
    private BigDecimal precoVenda;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();

    /** Nulo até a primeira alteração — a coluna é anulável na V25, ao contrário de cheques. */
    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PreUpdate
    void preUpdate() { this.atualizadoEm = LocalDateTime.now(); }
}
