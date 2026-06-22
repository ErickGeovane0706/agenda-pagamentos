package com.agenda.domain.usuario;

import com.agenda.domain.empresa.Empresa;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade de usuário do sistema. Cada usuário pertence a uma empresa
 * (tenant) e possui um perfil que define suas permissões.
 * Suporte a soft-delete via solicitouExclusao.
 */
@Entity
@Table(name = "usuarios")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(nullable = false, length = 200, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 300)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PerfilUsuario perfil;

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
