package com.agenda.domain.boleto;

import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.loja.Loja;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade JPA que representa um boleto a pagar no sistema.
 * <p>
 * Cada boleto pertence a uma {@link Empresa} (multi‑tenant) e a uma {@link Loja}.
 * O fluxo de vida é: {@code PENDENTE} → {@code PAGO} / {@code VENCIDO} / {@code CANCELADO}.
 * </p>
 *
 * <p>Regras de negócio:</p>
 * <ul>
 *   <li>{@code status} inicia como PENDENTE — não é informado na criação.</li>
 *   <li>{@code pagoEm} só é preenchido quando status = PAGO (service seta).</li>
 *   <li>{@code codigoBarras} e {@code arquivoKey} são independentes: um boleto pode
 *       ter apenas o código de barras, apenas o arquivo, ambos, ou nenhum.</li>
 *   <li>{@code atualizadoEm} é atualizado automaticamente pelo callback {@code @PreUpdate}.</li>
 * </ul>
 */
@Entity
@Table(name = "boletos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Boleto {
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
    private String fornecedor;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate vencimento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusBoleto status = StatusBoleto.PENDENTE;

    @Column(name = "codigo_barras", columnDefinition = "TEXT")
    private String codigoBarras;

    @Column(name = "url_arquivo")
    private String urlArquivo;

    @Column(name = "nome_arquivo", length = 300)
    private String nomeArquivo;

    @Column(name = "arquivo_key", length = 500)
    private String arquivoKey;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    /** Seta quando status muda para PAGO; limpa quando sai de PAGO. */
    @Column(name = "pago_em")
    private LocalDateTime pagoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "atualizado_em", nullable = false)
    @Builder.Default
    private LocalDateTime atualizadoEm = LocalDateTime.now();

    @PreUpdate
    void preUpdate() { this.atualizadoEm = LocalDateTime.now(); }
}
