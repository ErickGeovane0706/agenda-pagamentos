package com.agenda.domain.notificacao;

import com.agenda.domain.loja.LojaRepository;
import com.agenda.domain.uso.LimiteUsoService;
import com.agenda.domain.usuario.UsuarioRepository;
import com.agenda.shared.TenantContext;
import com.agenda.shared.UserContext;
import com.agenda.shared.exception.AccessDeniedException;
import com.agenda.shared.exception.NotFoundException;
import com.agenda.shared.exception.PagamentoRequeridoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
/**
 * Serviço de preferências de notificação.
 * <p>
 * Apenas o próprio usuário pode ver/alterar suas próprias preferências
 * (extraído do token JWT via {@link UserContext}). Lojas de outras empresas
 * são rejeitadas com {@link AccessDeniedException}.
 */
public class PreferenciaNotificacaoService {

    private final PreferenciaNotificacaoRepository preferenciaRepository;
    private final UsuarioRepository usuarioRepository;
    private final LojaRepository lojaRepository;
    private final LimiteUsoService limiteUsoService;

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

    /**
     * Cria ou substitui as preferências do usuário logado.
     * <p>
     * Valida que todas as lojas informadas pertencem à empresa do usuário
     * (mesmo padrão dos services BoletoService, PixService e ChequeService).
     * Se o usuário ainda não tinha preferência, uma nova entidade é criada
     * via {@code builder()} antes de popular os campos.
     *
     * @param req dados validados do formulário de configurações
     * @return DTO atualizado
     * @throws NotFoundException   se o usuário ou alguma loja não existir
     * @throws AccessDeniedException se alguma loja for de outra empresa
     */
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

        // Teto de destinatários: cada telefone ativo custa um template pago por
        // disparo, até 4 vezes ao dia. Só barra na TRANSIÇÃO para ativo — quem
        // já recebe lembrete não perde a vaga ao editar horário ou loja, o que
        // aconteceria se a contagem incluísse o próprio registro sendo salvo.
        if (req.whatsappAtivo() && !Boolean.TRUE.equals(preferencia.getWhatsappAtivo())) {
            int limite = limiteUsoService.limiteDestinatarios(empresaId);
            long ativos = preferenciaRepository.contarAtivosDaEmpresa(empresaId);
            if (ativos >= limite) {
                throw new PagamentoRequeridoException(
                    "Sua assinatura cobre " + limite + " telefone(s) recebendo lembrete e você já tem "
                        + ativos + ". Desative outro telefone ou contrate mais uma loja.");
            }
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