package com.agenda.domain.banco;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller CRUD de bancos.
 *
 * Regras de acesso:
 * - GET /api/bancos: qualquer usuário autenticado pode listar
 * - POST e PUT: ADMIN ou OPERADOR
 * - DELETE: apenas ADMIN
 * Todas as operações são isoladas por tenant (empresa do usuário logado).
 */
@RestController
@RequestMapping("/api/bancos")
@RequiredArgsConstructor
public class BancoController {

    private final BancoService bancoService;

    @GetMapping
    public ResponseEntity<List<BancoDTO>> listar() {
        return ResponseEntity.ok(bancoService.listar());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<BancoDTO> criar(@RequestBody @Valid CriarBancoRequest req) {
        return ResponseEntity.ok(bancoService.criar(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<BancoDTO> editar(@PathVariable UUID id,
                                            @RequestBody @Valid EditarBancoRequest req) {
        return ResponseEntity.ok(bancoService.editar(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        bancoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
