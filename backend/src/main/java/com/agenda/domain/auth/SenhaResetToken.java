package com.agenda.domain.auth;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Token de redefinição de senha (tabela criada na V18).
 *
 * Regras:
 * - O banco guarda apenas o SHA-256 do token; o valor cru só existe no
 *   email enviado. Dump do banco vazado não permite redefinir senha nenhuma.
 * - usado_em != null marca uso único: token que já redefiniu uma senha
 *   não redefine outra.
 * - expira_em fecha a janela (senha-reset.ttl-horas, default 2h).
 */
@Entity
@Table(name = "senha_reset_token")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SenhaResetToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "usado_em")
    private LocalDateTime usadoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();
}
