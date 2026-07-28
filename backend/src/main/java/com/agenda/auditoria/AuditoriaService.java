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

/**
 * Entidade JPA que persiste eventos de auditoria de todas as
 * operações CRUD (criação, alteração, exclusão) nos domínios
 * do sistema — banco, empresa, boleto, PIX, cheque, etc.
 * Cada registro inclui tenant (empresaId), usuário, IP,
 * ação, entidade afetada e descrição textual.
 */
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

/**
 * Service de auditoria. Registra eventos em transação separada
 * (REQUIRES_NEW) para garantir que a auditoria persista mesmo
 * se a transação principal falhar. Operações sem tenant definido
 * (como login) são ignoradas para evitar ruído.
 */
@Service
@RequiredArgsConstructor
public class AuditoriaService {
    private final AuditoriaRepository auditoriaRepository;

    /**
     * Registra um evento de auditoria em transação separada (REQUIRES_NEW)
     * para que a auditoria persista mesmo se a transação principal falhar.
     *
     * Regra de negócio: se não houver tenant (empresaId) na requisição,
     * o registro é ignorado — operações sem contexto de empresa (ex.:
     * login antes de autenticar) não são auditadas.
     */
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

    /**
     * Registra um evento que NÃO nasceu de uma requisição de usuário: webhook do
     * gateway de pagamento e jobs agendados.
     *
     * Existe porque {@link #registrar} desiste em silêncio quando não há
     * {@code TenantContext} — e é exatamente o caso desses caminhos. Usá-lo aqui
     * não gravaria nada e também não daria erro, deixando sem trilha justamente
     * os eventos que ninguém consegue reconstruir depois (ativação por pagamento,
     * inadimplência, rebaixamento por vencimento).
     *
     * Por isso o {@code empresaId} vem explícito, do próprio dado que está sendo
     * alterado. {@code usuarioId} e {@code ip} ficam nulos de propósito: não houve
     * pessoa nem navegador, e inventar um seria pior que a ausência — a origem
     * real vai em {@code detalhes}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarSistema(String acao, String entidade, UUID empresaId,
                                 UUID entidadeId, String detalhes) {
        var registro = Auditoria.builder()
            .empresaId(empresaId)
            .acao(acao)
            .entidade(entidade)
            .entidadeId(entidadeId)
            .detalhes(detalhes)
            .build();
        auditoriaRepository.save(registro);
    }
}
