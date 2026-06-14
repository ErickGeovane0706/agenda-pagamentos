package com.agenda.domain.pix;

import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.loja.Loja;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

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

    @Column(name = "chave_pix", nullable = false, columnDefinition = "TEXT")
    private String chavePix;

    @Column(name = "tipo_chave", length = 20)
    private String tipoChave;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "arquivo_key", length = 500)
    private String arquivoKey;

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
