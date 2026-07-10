package com.agenda.domain.usuario;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.shared.TenantContext;
import com.agenda.shared.UserContext;
import com.agenda.shared.exception.AccessDeniedException;
import com.agenda.shared.exception.NotFoundException;
import com.agenda.domain.empresa.Empresa;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.agenda.domain.empresa.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service de Usuário. Gerencia cadastro, edição, exclusão lógica
 * e autenticação. Inclui validação de senha e auditoria.
 * O master vê todos os usuários; demais perfis veem apenas
 * os do próprio tenant.
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public List<UsuarioDTO> listar() {
        if (UserContext.isMaster()) {
            return usuarioRepository.findAll().stream()
                .map(UsuarioDTO::from)
                .toList();
        }
        UUID empresaId = TenantContext.getEmpresaId();
        return usuarioRepository.findAll().stream()
            .filter(u -> u.getEmpresa().getId().equals(empresaId))
            .map(UsuarioDTO::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<UsuarioDTO> listarPorEmpresa(UUID empresaId) {
        return usuarioRepository.findByEmpresaId(empresaId).stream()
            .map(UsuarioDTO::from)
            .toList();
    }

    @Transactional
    public UsuarioDTO criar(CriarUsuarioRequest req) {
        if (req.perfil() == PerfilUsuario.MASTER && !UserContext.isMaster()) {
            throw new AccessDeniedException("Apenas MASTER pode criar usuário MASTER");
        }
        passwordValidator.validar(req.senha());
        UUID empresaId = TenantContext.getEmpresaId();
        var empresa = empresaRepository.getReferenceById(empresaId);

        var usuario = Usuario.builder()
            .empresa(empresa)
            .nome(req.nome())
            .email(req.email())
            .senhaHash(passwordEncoder.encode(req.senha()))
            .perfil(req.perfil())
            .build();
        usuario = usuarioRepository.save(usuario);
        auditoriaService.registrar("CRIAR", "USUARIO", usuario.getId(), "Email: " + req.email());
        return UsuarioDTO.from(usuario);
    }

    @Transactional
    public UsuarioDTO criarNaEmpresa(UUID empresaId, CriarUsuarioRequest req) {
        passwordValidator.validar(req.senha());
        var empresa = empresaRepository.findById(empresaId)
            .orElseThrow(() -> new NotFoundException("Empresa não encontrada"));

        var usuario = Usuario.builder()
            .empresa(empresa)
            .nome(req.nome())
            .email(req.email())
            .senhaHash(passwordEncoder.encode(req.senha()))
            .perfil(req.perfil())
            .build();
        usuario = usuarioRepository.save(usuario);
        auditoriaService.registrar("CRIAR", "USUARIO", usuario.getId(), "Email: " + req.email());
        return UsuarioDTO.from(usuario);
    }

    @Transactional
    public UsuarioDTO editar(UUID id, EditarUsuarioRequest req) {
        var usuario = usuarioRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
        if (!UserContext.isMaster()) {
            UUID empresaId = TenantContext.getEmpresaId();
            if (!usuario.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");
        }

        usuario.setNome(req.nome());
        usuario.setEmail(req.email());
        if (req.senha() != null && !req.senha().isBlank()) {
            passwordValidator.validar(req.senha());
            usuario.setSenhaHash(passwordEncoder.encode(req.senha()));
        }
        usuario = usuarioRepository.save(usuario);
        auditoriaService.registrar("EDITAR", "USUARIO", usuario.getId(), "Email: " + req.email());
        return UsuarioDTO.from(usuario);
    }

    @Transactional
    public void excluir(UUID id) {
        var usuario = usuarioRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
        if (!UserContext.isMaster()) {
            UUID empresaId = TenantContext.getEmpresaId();
            if (!usuario.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");
        }
        usuarioRepository.delete(usuario);
        auditoriaService.registrar("EXCLUIR", "USUARIO", id, "Email: " + usuario.getEmail());
    }

    /**
     * Marca APENAS o usuário logado para exclusão (anonimizado no job das 03:00).
     * Nunca afeta a empresa nem outros usuários — encerrar a empresa inteira é
     * um fluxo separado, deliberado.
     *
     * Bloqueia a auto-exclusão do último ADMIN ativo: deixar a empresa sem
     * nenhum administrador tornaria os dados órfãos (sem acesso, sem titular
     * responsável pela LGPD).
     */
    @Transactional
    public void solicitarExclusao() {
        UUID usuarioId = UserContext.getUsuarioId();
        var usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));

        if (usuario.getPerfil() == PerfilUsuario.ADMIN) {
            long adminsAtivos = usuarioRepository
                .countByEmpresaIdAndPerfilAndSolicitouExclusaoFalseAndExcluidoEmIsNull(
                    usuario.getEmpresa().getId(), PerfilUsuario.ADMIN);
            if (adminsAtivos <= 1) {
                throw new IllegalArgumentException(
                    "Você é o último administrador da empresa. Mantenha ao menos um administrador ativo ou encerre a empresa.");
            }
        }

        usuario.setSolicitouExclusao(true);
        usuario.setSolicitouExclusaoEm(LocalDateTime.now());
        usuarioRepository.save(usuario);
        auditoriaService.registrar("SOLICITAR_EXCLUSAO", "USUARIO", usuarioId, "Email: " + usuario.getEmail());
    }
}
