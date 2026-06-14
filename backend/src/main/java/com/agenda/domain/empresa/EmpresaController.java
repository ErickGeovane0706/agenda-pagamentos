package com.agenda.domain.empresa;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        empresaService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
