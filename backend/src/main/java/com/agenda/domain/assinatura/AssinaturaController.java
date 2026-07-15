package com.agenda.domain.assinatura;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller de Assinaturas. Restrito ao papel MASTER (super-admin):
 * é o painel manual de billing da Fase 1 — listar as empresas com seu
 * status e virar a linha (ativar/suspender/cancelar, ajustar lojas
 * contratadas e vigência) após o pagamento fora do sistema.
 */
@RestController
@RequestMapping("/api/assinaturas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MASTER')")
public class AssinaturaController {

    private final AssinaturaService assinaturaService;

    @GetMapping
    public ResponseEntity<List<AssinaturaDTO>> listar() {
        return ResponseEntity.ok(assinaturaService.listar());
    }

    @PutMapping("/{empresaId}")
    public ResponseEntity<AssinaturaDTO> atualizar(@PathVariable UUID empresaId,
                                                   @RequestBody @Valid AtualizarAssinaturaRequest req) {
        return ResponseEntity.ok(assinaturaService.atualizar(empresaId, req));
    }
}
