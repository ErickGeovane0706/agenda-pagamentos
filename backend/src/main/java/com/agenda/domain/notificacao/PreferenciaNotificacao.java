package com.agenda.domain.notificacao;

import com.agenda.domain.loja.Loja;
import com.agenda.domain.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "preferencias_notificacao")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PreferenciaNotificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Column(name = "telefone_whatsapp", length = 20)
    private String telefoneWhatsapp;

    @Column(name = "whatsapp_ativo", nullable = false)
    @Builder.Default
    private Boolean whatsappAtivo = false;

    @Column(name = "horario_1")
    private LocalTime horario1;

    @Column(name = "horario_2")
    private LocalTime horario2;

    @Column(name = "horario_3")
    private LocalTime horario3;

    @Column(name = "horario_4")
    private LocalTime horario4;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "usuario_lojas_notificacao",
            joinColumns = @JoinColumn(name = "usuario_id", referencedColumnName = "usuario_id", insertable = false, updatable = false),
            inverseJoinColumns = @JoinColumn(name = "loja_id")
    )
    @Builder.Default
    private Set<Loja> lojas = new HashSet<>();

    @Column(name = "criado_em", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "atualizado_em", nullable = false)
    @Builder.Default
    private LocalDateTime atualizadoEm = LocalDateTime.now();

    @PreUpdate
    void preUpdate() { this.atualizadoEm = LocalDateTime.now(); }

    /**
     * Retorna os horários configurados, em ordem, ignorando nulos.
     * Usado pelo scheduler para decidir se o momento atual bate com algum horário do usuário.
     */
    public java.util.List<LocalTime> getHorariosAtivos() {
        return java.util.stream.Stream.of(horario1, horario2, horario3, horario4)
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}