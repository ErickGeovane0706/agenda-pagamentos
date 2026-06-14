package com.agenda.domain.coluna;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/colunas-extras")
@RequiredArgsConstructor
public class ColunaExtraController {

    private final ColunaExtraService colunaExtraService;

    @GetMapping
    public ResponseEntity<List<ColunaExtraDTO>> listar(@RequestParam TipoPagamento tipoPagamento) {
        return ResponseEntity.ok(colunaExtraService.listar(tipoPagamento));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ColunaExtraDTO> criar(@RequestBody @Valid CriarColunaExtraRequest req) {
        return ResponseEntity.ok(colunaExtraService.criar(req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        colunaExtraService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/valores")
    public ResponseEntity<Map<UUID, String>> listarValores(
            @RequestParam UUID registroId,
            @RequestParam TipoPagamento tipoPagamento) {
        return ResponseEntity.ok(colunaExtraService.listarValores(registroId, tipoPagamento));
    }

    @PostMapping("/valores")
    public ResponseEntity<Void> salvarValores(@RequestBody @Valid SalvarValoresRequest req) {
        colunaExtraService.salvarValores(req.tipoPagamento(), req.registroId(), req.valores());
        return ResponseEntity.ok().build();
    }
}