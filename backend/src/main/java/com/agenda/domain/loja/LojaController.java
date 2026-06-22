package com.agenda.domain.loja;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller CRUD de lojas. Listar e buscar são abertos a
 * qualquer perfil autenticado; criar/editar/excluir exigem ADMIN.
 */
@RestController
@RequestMapping("/api/lojas")
@RequiredArgsConstructor
public class LojaController {

    private final LojaService lojaService;

    @GetMapping
    public ResponseEntity<List<LojaDTO>> listar() {
        return ResponseEntity.ok(lojaService.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<LojaDTO> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(lojaService.buscar(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LojaDTO> criar(@RequestBody @Valid CriarLojaRequest req) {
        return ResponseEntity.ok(lojaService.criar(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LojaDTO> editar(@PathVariable UUID id,
                                           @RequestBody @Valid EditarLojaRequest req) {
        return ResponseEntity.ok(lojaService.editar(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        lojaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
