package com.agenda.domain.venda;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Controller de vendas do PDV.
 * <p>
 * Vender exige {@code ADMIN} ou {@code OPERADOR} (decidido em 27/08); o
 * {@code VIEWER} só lê. Não existe {@code PUT}: venda é imutável, e corrigir é
 * cancelar (§5.3).
 * <p>
 * Todas as rotas passam antes pelo {@link com.agenda.security.PdvGateFilter}.
 */
@RestController
@RequestMapping("/api/vendas")
@RequiredArgsConstructor
public class VendaController {

    private final VendaService vendaService;

    @GetMapping
    public ResponseEntity<List<VendaDTO>> listar(
            @RequestParam UUID lojaId,
            @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(vendaService.listar(lojaId, pageable));
    }

    /**
     * Relatório do período. Vem <b>antes</b> de {@code /{id}} de propósito: com
     * a ordem invertida, o Spring casaria "relatorio" contra {@code {id}} e
     * responderia 400 de UUID inválido.
     */
    @GetMapping("/relatorio")
    public ResponseEntity<RelatorioVendasDTO> relatorio(
            @RequestParam UUID lojaId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return ResponseEntity.ok(vendaService.relatorio(lojaId, de, ate));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VendaDTO> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(vendaService.buscar(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<VendaDTO> criar(@RequestBody @Valid CriarVendaRequest req) {
        return ResponseEntity.ok(vendaService.criar(req));
    }

    /**
     * Cancela a venda e devolve o estoque. {@code POST} e não {@code DELETE}:
     * a venda não é removida, muda de status — o {@code DELETE} sugeriria que o
     * histórico desaparece, que é justamente o que não acontece.
     */
    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ResponseEntity<Void> cancelar(@PathVariable UUID id) {
        vendaService.cancelar(id);
        return ResponseEntity.noContent().build();
    }
}
