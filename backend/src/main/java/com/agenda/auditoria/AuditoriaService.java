package com.agenda.auditoria;

import com.agenda.shared.TenantContext;
import com.agenda.shared.UserContext;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "auditoria")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Auditoria {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "usuario_nome", length = 200)
    private String usuarioNome;

    @Column(nullable = false, length = 50)
    private String acao;

    @Column(nullable = false, length = 50)
    private String entidade;

    @Column(name = "entidade_id")
    private UUID entidadeId;

    @Column(columnDefinition = "TEXT")
    private String detalhes;

    @Column(length = 45)
    private String ip;

    @Column(name = "criado_em", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();
}

interface AuditoriaRepository extends org.springframework.data.jpa.repository.JpaRepository<Auditoria, UUID> {}

@Service
@RequiredArgsConstructor
public class AuditoriaService {
    private final AuditoriaRepository auditoriaRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String acao, String entidade, UUID entidadeId, String detalhes) {
        var empresaId = TenantContext.getEmpresaId();
        if (empresaId == null) return;

        var request = ((ServletRequestAttributes)
            RequestContextHolder.getRequestAttributes());
        var ip = request != null ? request.getRequest().getRemoteAddr() : null;

        var registro = Auditoria.builder()
            .empresaId(empresaId)
            .usuarioId(UserContext.getUsuarioId())
            .usuarioNome(UserContext.getNome())
            .acao(acao)
            .entidade(entidade)
            .entidadeId(entidadeId)
            .detalhes(detalhes)
            .ip(ip)
            .build();
        auditoriaRepository.save(registro);
    }
}
