package com.agenda.domain.arquivo;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Arquivo anexado a um lançamento (boleto, PIX ou cheque). Exatamente um dos
 * três ids vem preenchido — a constraint {@code anexos_um_lancamento} garante.
 */
@Entity
@Table(name = "anexos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Anexo {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "boleto_id")
    private UUID boletoId;

    @Column(name = "pix_id")
    private UUID pixId;

    @Column(name = "cheque_id")
    private UUID chequeId;

    @Column(name = "arquivo_key", nullable = false, length = 500)
    private String arquivoKey;

    @Column(name = "nome_arquivo", length = 300)
    private String nomeArquivo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();
}
