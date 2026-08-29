package com.agenda.domain.empresa;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller de Empresas. Restrito ao papel MASTER (super-admin).
 * Empresas são o nível mais alto do sistema — apenas o master
 * pode criar, editar ou excluir tenants.
 */
@RestController
@RequestMapping("/api/empresas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MASTER')")
public class EmpresaController {

    private final EmpresaService empresaService;

    @GetMapping
    public ResponseEntity<List<EmpresaDTO>> listar() {
        return ResponseEntity.ok(empresaService.listar());
    }

    @PostMapping
    public ResponseEntity<EmpresaDTO> criar(@RequestBody @Valid CriarEmpresaRequest req) {
        return ResponseEntity.ok(empresaService.criar(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmpresaDTO> editar(@PathVariable UUID id,
                                              @RequestBody @Valid EditarEmpresaRequest req) {
        return ResponseEntity.ok(empresaService.editar(id, req));
    }

    /**
     * Libera (ou revoga) o módulo de Estoque/PDV da empresa. Separado do
     * {@link #editar} porque não é dado cadastral: é entitlement, nasce
     * desligado e só o MASTER move — o ADMIN da empresa não liga o próprio
     * módulo.
     */
    @PutMapping("/{id}/pdv")
    public ResponseEntity<Void> definirPdv(@PathVariable UUID id,
                                           @RequestBody @Valid DefinirPdvRequest req) {
        empresaService.definirPdv(id, req.habilitado());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        empresaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
