package com.agenda.domain.notificacao;

import com.agenda.domain.loja.LojaRepository;
import com.agenda.domain.usuario.UsuarioRepository;
import com.agenda.shared.TenantContext;
import com.agenda.shared.UserContext;
import com.agenda.shared.exception.AccessDeniedException;
import com.agenda.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PreferenciaNotificacaoService {

    private final PreferenciaNotificacaoRepository preferenciaRepository;
    private final UsuarioRepository usuarioRepository;
    private final LojaRepository lojaRepository;

    /**
     * Retorna a preferência do usuário logado. Se ele ainda não tiver
     * configurado nada, devolve um DTO "vazio" em vez de erro — a tela de
     * configurações deve conseguir exibir o formulário em branco no primeiro acesso.
     */
    @Transactional(readOnly = true)
    public PreferenciaNotificacaoDTO buscarDoUsuarioLogado() {
        UUID usuarioId = UserContext.getUsuarioId();
        return preferenciaRepository.findByUsuarioId(usuarioId)
                .map(PreferenciaNotificacaoDTO::from)
                .orElseGet(() -> new PreferenciaNotificacaoDTO(
                        null, null, false, null, null, null, null, java.util.Set.of()
                ));
    }

    @Transactional
    public PreferenciaNotificacaoDTO atualizar(AtualizarPreferenciaNotificacaoRequest req) {
        UUID usuarioId = UserContext.getUsuarioId();
        UUID empresaId = TenantContext.getEmpresaId();

        var usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));

        var preferencia = preferenciaRepository.findByUsuarioId(usuarioId)
                .orElseGet(() -> PreferenciaNotificacao.builder().usuario(usuario).build());

        // Garante que o usuário só pode escolher lojas da própria empresa —
        // mesmo padrão de checagem usado no BoletoService/PixService/ChequeService.
        var lojaIdsRequest = req.lojaIds() != null ? req.lojaIds() : java.util.Set.<UUID>of();
        var lojaIdsValidados = new HashSet<UUID>();
        for (UUID lojaId : lojaIdsRequest) {
            var loja = lojaRepository.findById(lojaId)
                    .orElseThrow(() -> new NotFoundException("Loja não encontrada: " + lojaId));
            if (!loja.getEmpresa().getId().equals(empresaId)) {
                throw new AccessDeniedException("Acesso negado a loja de outra empresa");
            }
            lojaIdsValidados.add(lojaId);
        }

        preferencia.setTelefoneWhatsapp(req.telefoneWhatsapp());
        preferencia.setWhatsappAtivo(req.whatsappAtivo());
        preferencia.setHorario1(req.horario1());
        preferencia.setHorario2(req.horario2());
        preferencia.setHorario3(req.horario3());
        preferencia.setHorario4(req.horario4());
        preferencia.setLojaIds(lojaIdsValidados);

        preferencia = preferenciaRepository.save(preferencia);
        return PreferenciaNotificacaoDTO.from(preferencia);
    }
}