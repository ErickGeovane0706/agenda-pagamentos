package com.agenda.domain.cheque;

import com.agenda.domain.arquivo.ArquivoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Controller REST para operações CRUD de Cheques.
 * <p>
 * Estrutura idêntica a {@link com.agenda.domain.boleto.BoletoController} e
 * {@link com.agenda.domain.pix.PixController}. ADMIN/OPERADOR podem
 * modificar; visualização é pública para usuários autenticados.
 * Endpoint base: {@code /api/cheques}.
 * </p>
 */
@RestController
@RequestMapping("/api/cheques")
@RequiredArgsConstructor
public class ChequeController {

    private final ChequeService chequeService;
    private final ArquivoService arquivoService;

    @GetMapping
    public ResponseEntity<Page<ChequeDTO>> listar(
            @RequestParam(required = false) UUID lojaId,
            @RequestParam(required = false) StatusCheque status,
            @RequestParam(required = false) LocalDate de,
            @RequestParam(required = false) LocalDate ate,
            @RequestParam(required = false) String fornecedor,
            @PageableDefault(sort = "vencimento", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(chequeService.listar(lojaId, status, de, ate, fornecedor, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<ChequeDTO> criar(@RequestBody @Valid CriarChequeRequest req) {
        return ResponseEntity.ok(chequeService.criar(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<ChequeDTO> editar(@PathVariable UUID id,
                                              @RequestBody @Valid EditarChequeRequest req) {
        return ResponseEntity.ok(chequeService.editar(id, req));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<ChequeDTO> mudarStatus(@PathVariable UUID id,
                                                   @RequestBody @Valid MudarStatusChequeRequest req) {
        return ResponseEntity.ok(chequeService.mudarStatus(id, req.status()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        chequeService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/arquivo")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<ChequeDTO> uploadArquivo(@PathVariable UUID id,
                                                      @RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(chequeService.uploadArquivo(id, arquivo));
    }

    @GetMapping("/{id}/arquivo")
    public ResponseEntity<Map<String, String>> getUrlArquivo(@PathVariable UUID id) {
        var cheque = chequeService.buscarPorId(id);
        if (cheque.getArquivoKey() == null) {
            return ResponseEntity.notFound().build();
        }
        String url = arquivoService.gerarUrlTemporaria(cheque.getArquivoKey());
        return ResponseEntity.ok(Map.of("url", url));
    }

    @DeleteMapping("/{id}/arquivo")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<Void> deletarArquivo(@PathVariable UUID id) {
        var cheque = chequeService.buscarPorId(id);
        arquivoService.deletar(cheque.getArquivoKey());
        chequeService.removerArquivoKey(id);
        return ResponseEntity.noContent().build();
    }
}
