package com.agenda.domain.notificacao;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/preferencias-notificacao")
@RequiredArgsConstructor
public class PreferenciaNotificacaoController {

    private final PreferenciaNotificacaoService preferenciaNotificacaoService;

    @GetMapping("/minha")
    public ResponseEntity<PreferenciaNotificacaoDTO> buscarMinha() {
        return ResponseEntity.ok(preferenciaNotificacaoService.buscarDoUsuarioLogado());
    }

    @PutMapping("/minha")
    public ResponseEntity<PreferenciaNotificacaoDTO> atualizarMinha(
            @RequestBody @Valid AtualizarPreferenciaNotificacaoRequest req) {
        return ResponseEntity.ok(preferenciaNotificacaoService.atualizar(req));
    }
}