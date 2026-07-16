package com.agenda.domain.assinatura;

import com.agenda.shared.TenantContext;
import com.agenda.shared.UserContext;
import com.agenda.shared.exception.AccessDeniedException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller de Assinaturas.
 * <p>
 * A listagem e o ajuste manual seguem restritos ao MASTER (super-admin): é o
 * painel de billing — ver todas as empresas e virar a linha na mão.
 * <p>
 * Já {@code assinar} e {@code cancelar} são self-service: o ADMIN da própria
 * empresa contrata e encerra sozinho. Como o {@code empresaId} vem no path,
 * cada um desses chama {@link #exigirAcessoA} — sem isso, qualquer ADMIN
 * autenticado dispararia cobrança ou cancelaria a assinatura de outra empresa
 * só trocando o UUID da URL. O papel sozinho não autoriza; o tenant precisa
 * bater.
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
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<AssinaturaCheckoutDTO> assinar(@PathVariable UUID empresaId) {
        exigirAcessoA(empresaId);
        return ResponseEntity.ok(assinaturaService.assinar(empresaId));
    }

    /** Cancela a assinatura no gateway e marca CANCELADA. */
    @DeleteMapping("/{empresaId}/assinar")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<Void> cancelar(@PathVariable UUID empresaId) {
        exigirAcessoA(empresaId);
        assinaturaService.cancelarAssinatura(empresaId);
        return ResponseEntity.noContent().build();
    }

    /**
     * O MASTER opera qualquer empresa; os demais, só a sua. Mensagem propositalmente
     * igual à de empresa inexistente: distinguir "não é sua" de "não existe"
     * confirmaria a existência de empresas alheias para quem varre UUIDs.
     */
    private void exigirAcessoA(UUID empresaId) {
        if (UserContext.isMaster()) {
            return;
        }
        var tenant = TenantContext.getEmpresaId();
        if (tenant == null || !tenant.equals(empresaId)) {
            throw new AccessDeniedException("Assinatura não encontrada para a empresa");
        }
    }
}
