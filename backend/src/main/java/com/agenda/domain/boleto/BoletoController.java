package com.agenda.domain.boleto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller para operações CRUD de {@link Boleto}.
 * <p>
 * Endpoints protegidos por {@code @PreAuthorize} exigem role ADMIN ou OPERADOR.
 * A listagem é pública (autenticado) e suporta filtros opcionais via query params.
 * Os arquivos anexados ficam em {@link com.agenda.domain.arquivo.AnexoController}.
 * </p>
 */
@RestController
@RequestMapping("/api/boletos")
@RequiredArgsConstructor
public class BoletoController {

    private final BoletoService boletoService;

    /**
     * Lista paginada com filtros opcionais: loja, status, período, fornecedor.
     * Ordenação default por vencimento ASC, desempatada por fornecedor e por id — sem
     * os desempates o Postgres devolve os empates em ordem arbitrária e o mesmo item
     * pula de página entre uma consulta e outra.
     */
    @GetMapping
    public ResponseEntity<Page<BoletoDTO>> listar(
            @RequestParam(required = false) UUID lojaId,
            @RequestParam(required = false) StatusBoleto status,
            @RequestParam(required = false) LocalDate de,
            @RequestParam(required = false) LocalDate ate,
            @RequestParam(required = false) String fornecedor,
            @PageableDefault(sort = {"vencimento", "fornecedor", "id"}, direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(boletoService.listar(lojaId, status, de, ate, fornecedor, pageable));
    }

    /**
     * Retorna o índice (0-based) da primeira página com boleto pendente/vencido,
     * respeitando os mesmos filtros e o {@code size} da listagem. Usado pelo front
     * para abrir a aba já na primeira página que tem pendência.
     */
    @GetMapping("/pagina-pendente")
    public ResponseEntity<Map<String, Integer>> paginaPendente(
            @RequestParam(required = false) UUID lojaId,
            @RequestParam(required = false) StatusBoleto status,
            @RequestParam(required = false) LocalDate de,
            @RequestParam(required = false) LocalDate ate,
            @RequestParam(required = false) String fornecedor,
            @RequestParam(defaultValue = "15") int size) {
        return ResponseEntity.ok(Map.of("page",
                boletoService.paginaPendente(lojaId, status, de, ate, fornecedor, size)));
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

    /** Altera apenas o status (PENDENTE → PAGO / CANCELADO). O service decide se {@code pagoEm} é atualizado. */
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
}
