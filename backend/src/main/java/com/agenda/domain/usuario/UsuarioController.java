package com.agenda.domain.usuario;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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

    @PostMapping("/minha-conta/solicitar-exclusao")
    public ResponseEntity<Void> solicitarExclusao() {
        usuarioService.solicitarExclusao();
        return ResponseEntity.accepted().build();
    }
}
