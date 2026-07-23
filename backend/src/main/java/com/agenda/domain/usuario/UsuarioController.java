package com.agenda.domain.usuario;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller de Usuários. Endpoints de CRUD padrão + rotas
 * específicas do master (listarPorEmpresa, criarNaEmpresa)
 * + solicitação de exclusão da conta.
 */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
    public ResponseEntity<List<UsuarioDTO>> listar() {
        return ResponseEntity.ok(usuarioService.listar());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
    public ResponseEntity<UsuarioDTO> criar(@RequestBody @Valid CriarUsuarioRequest req) {
        return ResponseEntity.ok(usuarioService.criar(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
    public ResponseEntity<UsuarioDTO> editar(@PathVariable UUID id,
                                              @RequestBody @Valid EditarUsuarioRequest req) {
        return ResponseEntity.ok(usuarioService.editar(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        usuarioService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/empresa/{empresaId}")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<List<UsuarioDTO>> listarPorEmpresa(@PathVariable UUID empresaId) {
        return ResponseEntity.ok(usuarioService.listarPorEmpresa(empresaId));
    }

    @PostMapping("/empresa/{empresaId}")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<UsuarioDTO> criarNaEmpresa(@PathVariable UUID empresaId,
                                                      @RequestBody @Valid CriarUsuarioRequest req) {
        return ResponseEntity.ok(usuarioService.criarNaEmpresa(empresaId, req));
    }

    /**
     * Sem @PreAuthorize de perfil: o wizard de boas-vindas roda para qualquer
     * usuário logado, e a rota só alcança o dono do próprio token.
     */
    @PatchMapping("/minha-conta/onboarding")
    public ResponseEntity<UsuarioDTO> avancarOnboarding(@RequestBody @Valid AvancarOnboardingRequest req) {
        return ResponseEntity.ok(usuarioService.avancarOnboarding(req.etapa()));
    }

    @PostMapping("/minha-conta/solicitar-exclusao")
    @PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
    public ResponseEntity<Void> solicitarExclusao() {
        usuarioService.solicitarExclusao();
        return ResponseEntity.accepted().build();
    }
}
