package com.agenda.domain.cheque;

import com.agenda.domain.banco.Banco;
import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.loja.Loja;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade JPA que representa um Cheque a ser agendado/compensado.
 * <p>
 * Difere de boleto e PIX por ter:
 * <ul>
 *   <li>{@code banco} — ManyToOne opcional para {@link com.agenda.domain.banco.Banco}</li>
 *   <li>{@code numeroCheque} — número do cheque físico para rastreamento</li>
 *   <li>{@code compensadoEm} — data/hora da compensação (análogo a {@code pagoEm})</li>
 *   <li>{@code status} com {@code DEVOLVIDO} (sem fundos) em vez de {@code VENCIDO}</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "cheques")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Cheque {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loja_id", nullable = false)
    private Loja loja;

    /** Banco associado (opcional). Referência para conciliação. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "banco_id")
    private Banco banco;

    @Column(nullable = false, length = 300)
    private String fornecedor;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate vencimento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusCheque status = StatusCheque.PENDENTE;

    /** Número do cheque físico (opcional). Usado para conciliação bancária. */
    @Column(name = "numero_cheque", length = 50)
    private String numeroCheque;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "arquivo_key", length = 500)
    private String arquivoKey;

    /** Seta quando status muda para COMPENSADO; limpa quando sai de COMPENSADO. */
    @Column(name = "compensado_em")
    private LocalDateTime compensadoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "atualizado_em", nullable = false)
    @Builder.Default
    private LocalDateTime atualizadoEm = LocalDateTime.now();

    @PreUpdate
    void preUpdate() { this.atualizadoEm = LocalDateTime.now(); }
}
