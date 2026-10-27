package com.agenda.domain.arquivo;

import com.agenda.domain.boleto.BoletoService;
import com.agenda.domain.cheque.ChequeService;
import com.agenda.domain.pix.PixService;
import com.agenda.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Anexos de boleto, PIX e cheque: {@code /api/boletos/{id}/anexos},
 * {@code /api/pix/{id}/anexos} e {@code /api/cheques/{id}/anexos}.
 * <p>
 * Cada operação carrega antes o lançamento pelo {@code buscarPorId} do service
 * dele — é ali que o tenant é conferido.
 */
@RestController
@RequestMapping("/api/{recurso}/{id}/anexos")
@RequiredArgsConstructor
public class AnexoController {

    private final AnexoService anexoService;
    private final BoletoService boletoService;
    private final PixService pixService;
    private final ChequeService chequeService;

    @GetMapping
    public ResponseEntity<List<AnexoDTO>> listar(@PathVariable String recurso, @PathVariable UUID id) {
        var tipo = tipo(recurso);
        lojaDo(tipo, id);
        return ResponseEntity.ok(anexoService.listar(tipo, id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<AnexoDTO> adicionar(@PathVariable String recurso, @PathVariable UUID id,
                                              @RequestParam("arquivo") MultipartFile arquivo) {
        var tipo = tipo(recurso);
        return ResponseEntity.ok(anexoService.adicionar(tipo, id, lojaDo(tipo, id), arquivo));
    }

    /** URL assinada de curta duração para abrir o arquivo. */
    @GetMapping("/{anexoId}/url")
    public ResponseEntity<Map<String, String>> url(@PathVariable String recurso, @PathVariable UUID id,
                                                   @PathVariable UUID anexoId) {
        var tipo = tipo(recurso);
        lojaDo(tipo, id);
        return ResponseEntity.ok(Map.of("url", anexoService.gerarUrlTemporaria(tipo, id, anexoId)));
    }

    @DeleteMapping("/{anexoId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<Void> remover(@PathVariable String recurso, @PathVariable UUID id,
                                        @PathVariable UUID anexoId) {
        var tipo = tipo(recurso);
        lojaDo(tipo, id);
        anexoService.remover(tipo, id, anexoId);
        return ResponseEntity.noContent().build();
    }

    /** "boletos", "pix" ou "cheques" — o mesmo nome da pasta no bucket. */
    private static TipoDocumento tipo(String recurso) {
        return Arrays.stream(TipoDocumento.values())
                .filter(t -> t.getPasta().equals(recurso))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Recurso não encontrado"));
    }

    /** Confere o tenant do lançamento e devolve a loja dele (a pasta do arquivo no bucket). */
    private UUID lojaDo(TipoDocumento tipo, UUID id) {
        return switch (tipo) {
            case BOLETO -> boletoService.buscarPorId(id).getLoja().getId();
            case PIX -> pixService.buscarPorId(id).getLoja().getId();
            case CHEQUE -> chequeService.buscarPorId(id).getLoja().getId();
        };
    }
}
