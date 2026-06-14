package com.agenda.domain.banco;

import com.agenda.domain.empresa.Empresa;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "bancos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Banco {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(length = 10)
    private String codigo;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;
}
