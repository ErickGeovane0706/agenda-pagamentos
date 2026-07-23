package com.agenda.domain.assinatura;

import com.agenda.domain.empresa.DadosCobrancaRequest;
import com.agenda.domain.empresa.EmpresaService;
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
    private final EmpresaService empresaService;

    @GetMapping
    public ResponseEntity<List<AssinaturaDTO>> listar() {
        return ResponseEntity.ok(assinaturaService.listar());
    }

    /**
     * A assinatura de quem está logado. Não recebe empresaId no path de
     * propósito: resolve pelo tenant do token, e assim não há UUID alheio a
     * tentar. Para o MASTER (que não tem tenant próprio) o painel é o
     * {@link #listar()}.
     */
    @GetMapping("/minha")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','VIEWER')")
    public ResponseEntity<MinhaAssinaturaDTO> buscarMinha() {
        return ResponseEntity.ok(assinaturaService.buscarMinha(TenantContext.getEmpresaId()));
    }

    /**
     * Dados de cobrança da própria empresa. Mora aqui, e não em
     * {@code /api/empresas}, porque o {@code AssinaturaGateFilter} isenta
     * {@code /api/assinaturas}: em qualquer outro caminho, a empresa suspensa
     * levaria 402 ao tentar informar o CPF — e precisaria pagar para conseguir
     * preencher o que é exigido para pagar.
     */
    @PutMapping("/minha/cobranca")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> salvarDadosCobranca(@RequestBody @Valid DadosCobrancaRequest req) {
        empresaService.salvarDadosCobranca(TenantContext.getEmpresaId(), req);
        return ResponseEntity.noContent().build();
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
    public ResponseEntity<AssinaturaCheckoutDTO> assinar(@PathVariable UUID empresaId,
                                                         @RequestBody(required = false) @Valid AssinarRequest req) {
        exigirAcessoA(empresaId);
        return ResponseEntity.ok(assinaturaService.assinar(empresaId, req == null ? null : req.lojas()));
    }

    /**
     * Cliente já assinante contrata mais lojas. Separado do {@code assinar}
     * porque aqui não há checkout novo: a subscription que já corre passa a
     * valer outro valor, e a loja libera na hora.
     */
    @PutMapping("/minha/lojas")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AssinaturaDTO> contratarMaisLojas(@RequestBody @Valid AssinarRequest req) {
        return ResponseEntity.ok(
            assinaturaService.contratarMaisLojas(TenantContext.getEmpresaId(), req.lojas()));
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
