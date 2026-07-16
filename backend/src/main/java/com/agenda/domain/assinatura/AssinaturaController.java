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

    /**
     * Inicia a assinatura recorrente da empresa no gateway e devolve a URL de
     * pagamento (Pix/boleto). A ativação em si vem depois, pelo webhook, quando
     * o pagamento é confirmado. Idempotente no service (não duplica cobrança).
     */
    @PostMapping("/{empresaId}/assinar")
    public ResponseEntity<AssinaturaCheckoutDTO> assinar(@PathVariable UUID empresaId) {
        return ResponseEntity.ok(assinaturaService.assinar(empresaId));
    }

    /** Cancela a assinatura no gateway e marca CANCELADA. */
    @DeleteMapping("/{empresaId}/assinar")
    public ResponseEntity<Void> cancelar(@PathVariable UUID empresaId) {
        assinaturaService.cancelarAssinatura(empresaId);
        return ResponseEntity.noContent().build();
    }
}
