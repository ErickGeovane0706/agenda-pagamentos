package com.agenda.domain.notificacao;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/preferencias-notificacao")
@RequiredArgsConstructor
/**
 * Endpoints REST para o usuário logado consultar e alterar suas preferências
 * de notificação por WhatsApp. O prefixo /api/preferencias-notificacao é
 * protegido por JWT — o service extrai o usuário do token.
 */
public class PreferenciaNotificacaoController {

    private final PreferenciaNotificacaoService preferenciaNotificacaoService;

    /**
     * Retorna a preferência do usuário logado, ou um DTO vazio se ele nunca
     * configurou (para que a tela exiba o formulário em branco no primeiro acesso).
     */
    @GetMapping("/minha")
    public ResponseEntity<PreferenciaNotificacaoDTO> buscarMinha() {
        return ResponseEntity.ok(preferenciaNotificacaoService.buscarDoUsuarioLogado());
    }

    /**
     * Substitui completamente a preferência do usuário logado pelos dados
     * informados. Se o usuário ainda não tinha preferência, cria uma nova.
     * Valida que as lojas informadas pertencem à mesma empresa do usuário.
     */
    @PutMapping("/minha")
    public ResponseEntity<PreferenciaNotificacaoDTO> atualizarMinha(
            @RequestBody @Valid AtualizarPreferenciaNotificacaoRequest req) {
        return ResponseEntity.ok(preferenciaNotificacaoService.atualizar(req));
    }
}