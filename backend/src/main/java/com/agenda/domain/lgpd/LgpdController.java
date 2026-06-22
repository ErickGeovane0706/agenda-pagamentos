package com.agenda.domain.lgpd;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints para direitos do titular previstos na LGPD:
 * - GET /dados: direito de acesso (Art. 9º)
 * - POST /corrigir: direito de correção (Art. 18, III)
 * - GET /portabilidade: direito de portabilidade (Art. 18, V)
 * A exportação inclui dados do usuário, empresa, lojas e
 * todos os pagamentos (boletos, PIX, cheques) em JSON.
 */
@RestController
@RequestMapping("/api/lgpd")
@RequiredArgsConstructor
public class LgpdController {

    private final LgpdService lgpdService;

    @GetMapping("/dados")
    public ResponseEntity<LgpdDadosResponse> exportarDados() {
        return ResponseEntity.ok(lgpdService.exportarDados());
    }

    @PostMapping("/corrigir")
    public ResponseEntity<Void> solicitarCorrecao(@RequestBody @Valid LgpdCorrigirRequest req) {
        lgpdService.solicitarCorrecao(req);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/portabilidade")
    public ResponseEntity<String> portabilidade() {
        var dados = lgpdService.exportarDados();
        var json = """
            {"solicitado_em":"%s","dados":%s}
            """.formatted(java.time.LocalDateTime.now().toString(), toJson(dados));

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Content-Disposition", "attachment; filename=dados-lgpd.json");

        return ResponseEntity.ok().headers(headers).body(json);
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"erro\": \"Falha ao serializar dados\"}";
        }
    }
}
