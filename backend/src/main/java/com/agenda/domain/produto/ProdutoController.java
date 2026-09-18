package com.agenda.domain.produto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller de produtos do módulo de Estoque/PDV.
 * <p>
 * Escrita exige {@code ADMIN} ou {@code OPERADOR} e leitura é aberta a qualquer
 * perfil autenticado, como em boleto e cheque — o {@code VIEWER} só lê. O
 * {@code OPERADOR} escreve de propósito: ajustar a quantidade quando o estoque
 * físico diverge (§11) é trabalho de quem opera, não só do dono.
 * <p>
 * Todas as rotas daqui passam antes pelo {@link com.agenda.security.PdvGateFilter},
 * que responde 403 para empresa sem o módulo liberado pelo MASTER.
 */
@RestController
@RequestMapping("/api/produtos")
@RequiredArgsConstructor
public class ProdutoController {

    private final ProdutoService produtoService;

    /** {@code lojaId} é obrigatório: estoque é por loja, nunca por empresa (§4.1). */
    @GetMapping
    public ResponseEntity<List<ProdutoDTO>> listar(@RequestParam UUID lojaId) {
        return ResponseEntity.ok(produtoService.listar(lojaId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProdutoDTO> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(produtoService.buscar(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<ProdutoDTO> criar(@RequestBody @Valid CriarProdutoRequest req) {
        return ResponseEntity.ok(produtoService.criar(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<ProdutoDTO> editar(@PathVariable UUID id,
                                             @RequestBody @Valid EditarProdutoRequest req) {
        return ResponseEntity.ok(produtoService.editar(id, req));
    }

    /**
     * DELETE que <b>desativa</b>, não apaga. O verbo é o que o front espera para
     * "remover da lista", e apagar de verdade quebraria o histórico de vendas.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<Void> desativar(@PathVariable UUID id) {
        produtoService.desativar(id);
        return ResponseEntity.noContent().build();
    }
}
