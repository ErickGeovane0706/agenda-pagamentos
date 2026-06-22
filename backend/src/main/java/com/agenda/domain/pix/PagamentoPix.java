package com.agenda.domain.pix;

import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.loja.Loja;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade JPA que representa um pagamento via PIX a ser agendado.
 * <p>
 * Estrutura similar a {@link com.agenda.domain.boleto.Boleto}, mas
 * com {@code chavePix} e {@code tipoChave} no lugar de {@code codigoBarras}.
 * </p>
 *
 * <p>Regras de negócio:</p>
 * <ul>
 *   <li>{@code chavePix} é obrigatória — sem ela não é possível realizar o PIX.</li>
 *   <li>{@code tipoChave} é informativo (CPF, CNPJ, Email, Telefone, Aleatória).</li>
 *   <li>{@code status}, {@code pagoEm} e {@code atualizadoEm} seguem as mesmas
 *       regras de {@link com.agenda.domain.boleto.Boleto}.</li>
 * </ul>
 */
@Entity
@Table(name = "pagamentos_pix")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PagamentoPix {
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
    private StatusPix status = StatusPix.PENDENTE;

    /** Chave PIX para onde o pagamento deve ser enviado. Obrigatória. */
    @Column(name = "chave_pix", nullable = false, columnDefinition = "TEXT")
    private String chavePix;

    /** Classificação da chave: CPF, CNPJ, Email, Telefone, Aleatoria. Apenas informativo. */
    @Column(name = "tipo_chave", length = 20)
    private String tipoChave;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "arquivo_key", length = 500)
    private String arquivoKey;

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
