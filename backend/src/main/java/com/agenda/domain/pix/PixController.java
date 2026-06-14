package com.agenda.domain.pix;

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
@RequestMapping("/api/pix")
@RequiredArgsConstructor
public class PixController {

    private final PixService pixService;
    private final ArquivoService arquivoService;

    @GetMapping
    public ResponseEntity<Page<PixDTO>> listar(
            @RequestParam(required = false) UUID lojaId,
            @RequestParam(required = false) StatusPix status,
            @RequestParam(required = false) LocalDate de,
            @RequestParam(required = false) LocalDate ate,
            Pageable pageable) {
        return ResponseEntity.ok(pixService.listar(lojaId, status, de, ate, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<PixDTO> criar(@RequestBody @Valid CriarPixRequest req) {
        return ResponseEntity.ok(pixService.criar(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<PixDTO> editar(@PathVariable UUID id,
                                          @RequestBody @Valid EditarPixRequest req) {
        return ResponseEntity.ok(pixService.editar(id, req));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PixDTO> mudarStatus(@PathVariable UUID id,
                                               @RequestBody @Valid MudarStatusPixRequest req) {
        return ResponseEntity.ok(pixService.mudarStatus(id, req.status()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        pixService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/arquivo")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<PixDTO> uploadArquivo(@PathVariable UUID id,
                                                  @RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(pixService.uploadArquivo(id, arquivo));
    }

    @GetMapping("/{id}/arquivo")
    public ResponseEntity<Map<String, String>> getUrlArquivo(@PathVariable UUID id) {
        var pix = pixService.buscarPorId(id);
        if (pix.getArquivoKey() == null) {
            return ResponseEntity.notFound().build();
        }
        String url = arquivoService.gerarUrlTemporaria(pix.getArquivoKey());
        return ResponseEntity.ok(Map.of("url", url));
    }

    @DeleteMapping("/{id}/arquivo")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<Void> deletarArquivo(@PathVariable UUID id) {
        var pix = pixService.buscarPorId(id);
        arquivoService.deletar(pix.getArquivoKey());
        pixService.removerArquivoKey(id);
        return ResponseEntity.noContent().build();
    }
}
