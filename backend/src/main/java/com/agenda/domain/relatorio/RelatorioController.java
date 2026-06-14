package com.agenda.domain.relatorio;

import com.agenda.shared.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
public class RelatorioController {

    private final RelatorioService relatorioService;
    private final RelatorioExcelService relatorioExcelService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> gerar(
            @RequestParam LocalDate de,
            @RequestParam LocalDate ate,
            @RequestParam(required = false) UUID lojaId,
            @RequestParam(required = false, defaultValue = "TODOS") String tipo,
            @RequestParam(required = false) String status) {
        UUID empresaId = TenantContext.getEmpresaId();
        return ResponseEntity.ok(relatorioService.gerar(empresaId, de, ate, lojaId, tipo, status));
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> download(
            @RequestParam LocalDate de,
            @RequestParam LocalDate ate,
            @RequestParam(required = false) UUID lojaId,
            @RequestParam(required = false, defaultValue = "TODOS") String tipo,
            @RequestParam(required = false) String status) {
        UUID empresaId = TenantContext.getEmpresaId();
        var csv = relatorioService.gerarCsv(empresaId, de, ate, lojaId, tipo, status);
        var headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8));
        headers.setContentDispositionFormData("attachment", "relatorio-" + de + "-a-" + ate + ".csv");
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam LocalDate de,
            @RequestParam LocalDate ate,
            @RequestParam(required = false) UUID lojaId) throws IOException {
        UUID empresaId = TenantContext.getEmpresaId();
        var arquivo = relatorioExcelService.gerarExcel(empresaId, lojaId, de, ate);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "relatorio-" + de + "-a-" + ate + ".xlsx");
        return ResponseEntity.ok().headers(headers).body(arquivo);
    }
}
