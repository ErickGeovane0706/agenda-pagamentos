package com.agenda.domain.notificacao;

import com.agenda.domain.boleto.Boleto;
import com.agenda.domain.boleto.BoletoRepository;
import com.agenda.domain.cheque.Cheque;
import com.agenda.domain.cheque.ChequeRepository;
import com.agenda.domain.loja.Loja;
import com.agenda.domain.pix.PagamentoPix;
import com.agenda.domain.pix.PagamentoPixRepository;
import com.agenda.whatsapp.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Job agendado que verifica periodicamente compromissos a vencer
 * e envia notificações WhatsApp para os usuários com preferência
 * ativa. Executa em horário comercial (08:00–18:00, exceto fins
 * de semana). Notifica 1 dia antes e no próprio dia do vencimento.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacaoWhatsAppScheduler {

    private final PreferenciaNotificacaoRepository preferenciaRepository;
    private final BoletoRepository boletoRepository;
    private final PagamentoPixRepository pixRepository;
    private final ChequeRepository chequeRepository;
    private final WhatsAppService whatsAppService;
    private final MensagemNotificacaoBuilder mensagemBuilder = new MensagemNotificacaoBuilder();

    /**
     * Roda a cada 15 minutos. Para cada usuário com WhatsApp ativo, verifica
     * se o horário atual bate com algum dos 4 horários configurados E se,
     * dado o nível de atraso das pendências dele, esse horário específico
     * deve disparar (ver regra de degradação em deveEnviarNesteHorario).
     *
     * Roda a cada minuto: o usuário pode cadastrar qualquer horário (ex: 15:20),
     * não só múltiplos de 15 minutos, então o job precisa checar minuto a minuto
     * para não perder o horário configurado.
     */
    @Transactional(readOnly = true)
    @Scheduled(cron = "0 * * * * *")
    public void verificarEEnviarNotificacoes() {
        LocalTime agora = LocalTime.now().withSecond(0).withNano(0);
        LocalDate hoje = LocalDate.now();

        List<PreferenciaNotificacao> preferencias = preferenciaRepository.findAtivosComHorario(agora);

        if (preferencias.isEmpty()) {
            return;
        }

        log.info("Verificando notificações para {} usuário(s) no horário {}", preferencias.size(), agora);

        for (PreferenciaNotificacao pref : preferencias) {
            try {
                processarNotificacao(pref, agora, hoje);
            } catch (Exception e) {
                // Uma falha em um usuário não deve impedir o envio para os demais.
                log.error("Erro ao processar notificação do usuário {}: {}",
                        pref.getUsuario().getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Monta as listas de pendências do usuário e, se o horário atual for
     * elegível (de acordo com a regra de degradação de frequência), envia
     * o resumo por WhatsApp.
     * <p>
     * Se o usuário não tiver lojas selecionadas, pula sem erro.
     */
    private void processarNotificacao(PreferenciaNotificacao pref, LocalTime agora, LocalDate hoje) {
        UUID empresaId = pref.getUsuario().getEmpresa().getId();
        Set<UUID> lojaIds = pref.getLojaIds();

        if (lojaIds.isEmpty()) {
            log.info("Usuário {} não tem lojas selecionadas para notificação, pulando.", pref.getUsuario().getId());
            return;
        }

        List<PendenciaNotificacao> vencidos = buscarVencidos(empresaId, lojaIds, hoje);
        List<PendenciaNotificacao> venceHoje = buscarVenceHoje(empresaId, lojaIds, hoje);
        List<PendenciaNotificacao> venceFimDeSemana = buscarVenceFimDeSemana(empresaId, lojaIds, hoje);

        long maiorAtraso = vencidos.stream()
                .mapToLong(p -> p.diasAtraso(hoje))
                .max()
                .orElse(0);

        if (!deveEnviarNesteHorario(maiorAtraso, pref, agora)) {
            log.info("Horário {} pulado para usuário {} (atraso de {} dias já está em frequência reduzida)",
                    agora, pref.getUsuario().getId(), maiorAtraso);
            return;
        }

        String mensagem = mensagemBuilder.construir(vencidos, venceHoje, venceFimDeSemana, hoje);
        whatsAppService.enviar(pref.getTelefoneWhatsapp(), mensagem);
    }

    /**
     * Decide se ESTE horário específico (entre os até 4 configurados) deve
     * disparar, dado o maior atraso entre as pendências vencidas do usuário.
     *
     * Regra combinada:
     *  - 0 a 3 dias de atraso (ou nada vencido): todos os horários disparam
     *  - 4 a 10 dias: só o 1º e o último horário configurado
     *  - 11+ dias: só o 1º horário configurado
     *
     * Importante: isso só restringe quando o ÚNICO motivo de notificar fosse
     * um atraso antigo. Como já filtramos antes (vencidos + hoje + fim de
     * semana), na prática essa função apenas decide a frequência baseada no
     * pior caso — se o usuário tem algo vencendo hoje, o atraso mais antigo
     * ainda aparece na mesma mensagem, mas a cadência geral é dada pelo nível
     * de atraso mais crítico.
     */
    private boolean deveEnviarNesteHorario(long maiorAtraso, PreferenciaNotificacao pref, LocalTime agora) {
        List<LocalTime> horarios = pref.getHorariosAtivos();

        // Comparação por hora/minuto, não por igualdade exata do LocalTime:
        // o valor que volta do banco pode trazer segundos/nanossegundos
        // residuais (depende de como o driver JDBC mapeia a coluna TIME),
        // o que faria um indexOf() por igualdade total nunca encontrar
        // correspondência mesmo quando hora e minuto já batem.
        int indice = -1;
        for (int i = 0; i < horarios.size(); i++) {
            LocalTime h = horarios.get(i);
            if (h.getHour() == agora.getHour() && h.getMinute() == agora.getMinute()) {
                indice = i;
                break;
            }
        }

        if (indice == -1) {
            return false;
        }

        if (maiorAtraso <= 3) {
            return true;
        } else if (maiorAtraso <= 10) {
            return indice == 0 || indice == horarios.size() - 1;
        } else {
            return indice == 0;
        }
    }

    /**
     * Busca Boletos, PIX e Cheques com vencimento anterior a hoje
     * (independentemente do status) e filtra apenas os das lojas que o
     * usuário selecionou. O resultado é ordenado por vencimento (mais
     * antigo primeiro) para gerar a seção "VENCIDOS" da mensagem.
     */
    private List<PendenciaNotificacao> buscarVencidos(UUID empresaId, Set<UUID> lojaIds, LocalDate hoje) {
        List<PendenciaNotificacao> resultado = new ArrayList<>();

        for (Boleto b : boletoRepository.findVencidos(empresaId, hoje)) {
            if (lojaIds.contains(b.getLoja().getId())) {
                resultado.add(toPendencia(PendenciaNotificacao.TipoPendencia.BOLETO, b.getLoja(),
                        b.getFornecedor(), b.getValor(), b.getVencimento()));
            }
        }
        for (PagamentoPix p : pixRepository.findVencidos(empresaId, hoje)) {
            if (lojaIds.contains(p.getLoja().getId())) {
                resultado.add(toPendencia(PendenciaNotificacao.TipoPendencia.PIX, p.getLoja(),
                        p.getFornecedor(), p.getValor(), p.getVencimento()));
            }
        }
        for (Cheque c : chequeRepository.findVencidos(empresaId, hoje)) {
            if (lojaIds.contains(c.getLoja().getId())) {
                resultado.add(toPendencia(PendenciaNotificacao.TipoPendencia.CHEQUE, c.getLoja(),
                        c.getFornecedor(), c.getValor(), c.getVencimento()));
            }
        }

        resultado.sort((a, b) -> a.vencimento().compareTo(b.vencimento()));
        return resultado;
    }

    /**
     * Busca Boletos, PIX e Cheques cujo vencimento é exatamente hoje
     * e filtra pelas lojas do usuário. Gera a seção "VENCE HOJE".
     */
    private List<PendenciaNotificacao> buscarVenceHoje(UUID empresaId, Set<UUID> lojaIds, LocalDate hoje) {
        List<PendenciaNotificacao> resultado = new ArrayList<>();

        for (Boleto b : boletoRepository.findPendentesVencendoEm(empresaId, hoje)) {
            if (lojaIds.contains(b.getLoja().getId())) {
                resultado.add(toPendencia(PendenciaNotificacao.TipoPendencia.BOLETO, b.getLoja(),
                        b.getFornecedor(), b.getValor(), b.getVencimento()));
            }
        }
        for (PagamentoPix p : pixRepository.findPendentesVencendoEm(empresaId, hoje)) {
            if (lojaIds.contains(p.getLoja().getId())) {
                resultado.add(toPendencia(PendenciaNotificacao.TipoPendencia.PIX, p.getLoja(),
                        p.getFornecedor(), p.getValor(), p.getVencimento()));
            }
        }
        for (Cheque c : chequeRepository.findPendentesVencendoEm(empresaId, hoje)) {
            if (lojaIds.contains(c.getLoja().getId())) {
                resultado.add(toPendencia(PendenciaNotificacao.TipoPendencia.CHEQUE, c.getLoja(),
                        c.getFornecedor(), c.getValor(), c.getVencimento()));
            }
        }

        return resultado;
    }

    /**
     * Pendências vencendo no próximo sábado/domingo. Só faz sentido buscar
     * isso quando hoje é sexta-feira (aviso antecipado de cortesia) — nos
     * outros dias da semana, essa lista naturalmente vem vazia porque o
     * intervalo de datas não vai bater com nada relevante ainda.
     */
    private List<PendenciaNotificacao> buscarVenceFimDeSemana(UUID empresaId, Set<UUID> lojaIds, LocalDate hoje) {
        if (hoje.getDayOfWeek() != DayOfWeek.FRIDAY) {
            return List.of();
        }

        LocalDate sabado = hoje.plusDays(1);
        LocalDate domingo = hoje.plusDays(2);

        List<PendenciaNotificacao> resultado = new ArrayList<>();

        for (Boleto b : boletoRepository.findPendentesEntre(empresaId, sabado, domingo)) {
            if (lojaIds.contains(b.getLoja().getId())) {
                resultado.add(toPendencia(PendenciaNotificacao.TipoPendencia.BOLETO, b.getLoja(),
                        b.getFornecedor(), b.getValor(), b.getVencimento()));
            }
        }
        for (PagamentoPix p : pixRepository.findPendentesEntre(empresaId, sabado, domingo)) {
            if (lojaIds.contains(p.getLoja().getId())) {
                resultado.add(toPendencia(PendenciaNotificacao.TipoPendencia.PIX, p.getLoja(),
                        p.getFornecedor(), p.getValor(), p.getVencimento()));
            }
        }
        for (Cheque c : chequeRepository.findPendentesEntre(empresaId, sabado, domingo)) {
            if (lojaIds.contains(c.getLoja().getId())) {
                resultado.add(toPendencia(PendenciaNotificacao.TipoPendencia.CHEQUE, c.getLoja(),
                        c.getFornecedor(), c.getValor(), c.getVencimento()));
            }
        }

        return resultado;
    }

    private PendenciaNotificacao toPendencia(
            PendenciaNotificacao.TipoPendencia tipo, Loja loja,
            String fornecedor, java.math.BigDecimal valor, LocalDate vencimento) {
        return new PendenciaNotificacao(tipo, loja.getId(), loja.getNome(), fornecedor, valor, vencimento);
    }
}