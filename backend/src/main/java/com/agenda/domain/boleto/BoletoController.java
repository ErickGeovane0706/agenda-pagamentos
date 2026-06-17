package com.agenda.domain.boleto;

import com.agenda.domain.arquivo.ArquivoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/boletos")
@RequiredArgsConstructor
public class BoletoController {

    private final BoletoService boletoService;
    private final ArquivoService arquivoService;

    @GetMapping
    public ResponseEntity<Page<BoletoDTO>> listar(
            @RequestParam(required = false) UUID lojaId,
            @RequestParam(required = false) StatusBoleto status,
            @RequestParam(required = false) LocalDate de,
            @RequestParam(required = false) LocalDate ate,
            @RequestParam(required = false) String fornecedor,
            Pageable pageable) {
        return ResponseEntity.ok(boletoService.listar(lojaId, status, de, ate, fornecedor, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<BoletoDTO> criar(@RequestBody @Valid CriarBoletoRequest req) {
        return ResponseEntity.ok(boletoService.criar(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<BoletoDTO> editar(@PathVariable UUID id,
                                             @RequestBody @Valid EditarBoletoRequest req) {
        return ResponseEntity.ok(boletoService.editar(id, req));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<BoletoDTO> mudarStatus(@PathVariable UUID id,
                                                   @RequestBody @Valid MudarStatusRequest req) {
        return ResponseEntity.ok(boletoService.mudarStatus(id, req.status()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        boletoService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/arquivo")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<BoletoDTO> uploadArquivo(@PathVariable UUID id,
                                                     @RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(boletoService.uploadArquivo(id, arquivo));
    }

    @GetMapping("/{id}/arquivo")
    public ResponseEntity<Map<String, String>> getUrlArquivo(@PathVariable UUID id) {
        var boleto = boletoService.buscarPorId(id);
        if (boleto.getArquivoKey() == null) {
            return ResponseEntity.notFound().build();
        }
        String url = arquivoService.gerarUrlTemporaria(boleto.getArquivoKey());
        return ResponseEntity.ok(Map.of("url", url));
    }

    @DeleteMapping("/{id}/arquivo")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<Void> deletarArquivo(@PathVariable UUID id) {
        var boleto = boletoService.buscarPorId(id);
        arquivoService.deletar(boleto.getArquivoKey());
        boletoService.removerArquivoKey(id);
        return ResponseEntity.noContent().build();
    }
}
