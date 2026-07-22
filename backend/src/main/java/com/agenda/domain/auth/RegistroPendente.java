package com.agenda.domain.auth;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cadastro público aguardando confirmação de email (tabela criada na V22).
 * <p>
 * Guarda tudo que o visitante respondeu ANTES de existir tenant nenhum. O
 * provisionamento — empresa, loja, usuário e trial — só acontece no clique do
 * link, em {@code RegistroService.confirmar}.
 * <p>
 * Diferenças propositais em relação ao {@link SenhaResetToken}:
 * <ul>
 *   <li><b>Não tem {@code usado_em}:</b> a linha é APAGADA ao confirmar. Além de
 *       dar uso único de graça, evita reter senha e email de alguém que já virou
 *       usuário de verdade — o dado passa a viver em {@code usuarios}, e não em
 *       duplicidade aqui.</li>
 *   <li><b>{@code email} não é unique:</b> a pessoa pode pedir o cadastro duas
 *       vezes antes de confirmar. Quem confirmar primeiro vence; o segundo
 *       esbarra no unique de {@code usuarios.email}, que é mapeado para resposta
 *       neutra.</li>
 * </ul>
 */
@Entity
@Table(name = "registro_pendente")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RegistroPendente {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Vira {@code empresas.nome} E {@code lojas.nome} — uma pergunta só no formulário. */
    @Column(name = "nome_negocio", nullable = false, length = 200)
    private String nomeNegocio;

    @Column(name = "nome_usuario", nullable = false, length = 200)
    private String nomeUsuario;

    @Column(nullable = false, length = 200)
    private String email;

    /** BCrypt já aplicado no POST — a senha em claro nunca é persistida. */
    @Column(name = "senha_hash", nullable = false, length = 300)
    private String senhaHash;

    /** SHA-256 em hex. O token cru só existe no email. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();
}
