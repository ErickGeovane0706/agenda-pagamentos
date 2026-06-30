package com.agenda.domain.whatsappagent;

import com.agenda.domain.boleto.Boleto;
import com.agenda.domain.boleto.BoletoRepository;
import com.agenda.domain.boleto.BoletoSpecification;
import com.agenda.domain.boleto.StatusBoleto;
import com.agenda.domain.cheque.Cheque;
import com.agenda.domain.cheque.ChequeRepository;
import com.agenda.domain.cheque.ChequeSpecification;
import com.agenda.domain.cheque.StatusCheque;
import com.agenda.domain.loja.Loja;
import com.agenda.domain.loja.LojaRepository;
import com.agenda.domain.notificacao.PreferenciaNotificacao;
import com.agenda.domain.notificacao.PreferenciaNotificacaoRepository;
import com.agenda.domain.pix.PagamentoPix;
import com.agenda.domain.pix.PagamentoPixRepository;
import com.agenda.domain.pix.PagamentoPixSpecification;
import com.agenda.domain.pix.StatusPix;
import com.agenda.whatsapp.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ponto de entrada do agente conversacional: recebe o telefone + texto de
 * uma mensagem do webhook do WhatsApp, identifica o usuário, classifica a
 * intenção (via {@link IntentClassifierService}) e busca os dados reais
 * nos repositories já existentes do sistema — nunca na LLM.
 * <p>
 * Regra de segurança central: SEMPRE identifica o usuário pelo telefone
 * ANTES de processar a intenção. Se o telefone não estiver cadastrado
 * (ou tiver WhatsApp desativado), responde uma mensagem genérica e
 * encerra — nenhuma consulta ao banco financeiro acontece nesse caso.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppAgentService {

    private final PreferenciaNotificacaoRepository preferenciaRepository;
    private final LojaRepository lojaRepository;
    private final BoletoRepository boletoRepository;
    private final PagamentoPixRepository pixRepository;
    private final ChequeRepository chequeRepository;
    private final IntentClassifierService classifierService;
    private final RespostaFormatterService respostaFormatter;
    private final WhatsAppService whatsAppService;

    /**
     * Processa uma mensagem recebida do webhook. Não lança exceção — toda
     * falha interna resulta numa mensagem de erro amigável ao cliente, para
     * nunca deixar o WhatsApp "no vácuo" depois de uma pergunta.
     * <p>
     * {@code @Async}: o controller do webhook já respondeu HTTP 200 para a
     * Meta antes de chamar este método — a Meta exige resposta rápida
     * (poucos segundos) e este método pode levar mais que isso (chamada à
     * LLM + consultas ao banco). Roda na thread pool assíncrona padrão do
     * Spring (ver {@code @EnableAsync} em {@code AgendaApplication}).
     */
    @Async
    @Transactional(readOnly = true)
    public void processarMensagem(String telefoneOrigem, String textoMensagem) {
        Optional<PreferenciaNotificacao> preferenciaOpt = buscarPreferenciaPorTelefone(telefoneOrigem);

        if (preferenciaOpt.isEmpty()) {
            log.info("[WHATSAPP-AGENTE] Telefone {} não reconhecido, ignorando consulta.", mascarar(telefoneOrigem));
            whatsAppService.enviarMensagemTexto(telefoneOrigem,
                    "Não localizei esse número no nosso cadastro. Fale com o administrador da sua empresa para vincular seu WhatsApp.");
            return;
        }

        PreferenciaNotificacao pref = preferenciaOpt.get();
        UUID empresaId = pref.getUsuario().getEmpresa().getId();

        try {
            ResultadoClassificacao resultado = classifierService.classificar(textoMensagem);
            log.info("[WHATSAPP-AGENTE] Usuário {} | intenção classificada: {}", pref.getUsuario().getId(), resultado.getIntencao());

            String resposta = rotear(empresaId, pref, resultado);
            whatsAppService.enviarMensagemTexto(telefoneOrigem, resposta);

        } catch (Exception e) {
            log.error("[WHATSAPP-AGENTE] Erro ao processar mensagem do usuário {}: {}",
                    pref.getUsuario().getId(), e.getMessage(), e);
            whatsAppService.enviarMensagemTexto(telefoneOrigem,
                    "Tive um problema para consultar isso agora. Tenta de novo em alguns instantes.");
        }
    }

    /**
     * Busca a preferência pelo telefone tentando as variantes possíveis do
     * nono dígito (ver {@link #gerarVariantesTelefoneBr}). Necessário porque
     * a Meta Cloud API pode enviar o {@code from} do webhook sem o nono
     * dígito mesmo quando o número cadastrado em Configurações tem 13
     * dígitos (com o 9) — ou vice-versa, se o usuário cadastrar sem o 9.
     */
    private Optional<PreferenciaNotificacao> buscarPreferenciaPorTelefone(String telefone) {
        String normalizado = telefone.replaceAll("[^0-9]", "");
        List<String> variantes = gerarVariantesTelefoneBr(normalizado);
        List<PreferenciaNotificacao> encontrados = preferenciaRepository.findByTelefoneNormalizadoIn(variantes);

        if (encontrados.size() > 1) {
            log.warn("[WHATSAPP-AGENTE] Telefone {} casou com {} cadastros diferentes — usando o primeiro.",
                    mascarar(telefone), encontrados.size());
        }
        return encontrados.stream().findFirst();
    }

    /**
     * Números de celular no Brasil têm o formato 55 + DDD(2) + 9 + XXXXXXXX(8),
     * mas a Meta Cloud API às vezes envia (ou o usuário cadastra) a variante
     * antiga sem o nono dígito: 55 + DDD(2) + XXXXXXXX(8). Geramos as duas
     * formas possíveis e deixamos o repositório decidir qual existe.
     * <p>
     * Lista tem no máximo 2 elementos — a consulta resultante usa o mesmo
     * índice/coluna de sempre, sem impacto de performance relevante.
     */
    private List<String> gerarVariantesTelefoneBr(String normalizado) {
        List<String> variantes = new java.util.ArrayList<>();
        variantes.add(normalizado);

        boolean candidatoBrasil = normalizado.startsWith("55");
        if (candidatoBrasil) {
            String resto = normalizado.substring(2); // remove "55"
            if (resto.length() == 11 && resto.charAt(2) == '9') {
                // formato com 9: DDD(2) + 9 + numero(8) -> gera a variante sem o 9
                variantes.add("55" + resto.substring(0, 2) + resto.substring(3));
            } else if (resto.length() == 10) {
                // formato sem 9: DDD(2) + numero(8) -> gera a variante com o 9
                variantes.add("55" + resto.substring(0, 2) + "9" + resto.substring(2));
            }
        }
        return variantes;
    }

    private String rotear(UUID empresaId, PreferenciaNotificacao pref, ResultadoClassificacao resultado) {
        FiltrosAgente filtros = resultado.getFiltros();
        UUID lojaId = resolverLoja(empresaId, filtros.getLoja());

        // Loja mencionada mas não encontrada na empresa: avisa em vez de ignorar o filtro silenciosamente.
        if (filtros.getLoja() != null && !filtros.getLoja().isBlank() && lojaId == null) {
            return "Não encontrei nenhuma loja chamada \"" + filtros.getLoja() + "\" no seu cadastro. Pode confirmar o nome?";
        }

        // Loja mencionada existe, mas o usuário não tem acesso a ela via suas preferências.
        if (lojaId != null && !pref.getLojaIds().isEmpty() && !pref.getLojaIds().contains(lojaId)) {
            return "Você não tem acesso à loja \"" + filtros.getLoja() + "\" pelas suas permissões atuais.";
        }

        return switch (resultado.getIntencao()) {
            case CONSULTAR_PENDENCIAS -> consultarPendencias(empresaId, pref, lojaId, filtros);
            case OBTER_DADOS_PAGAMENTO -> obterDadosPagamento(empresaId, pref, lojaId, filtros);
            case OBTER_DADOS_CHEQUE -> obterDadosCheque(empresaId, pref, lojaId, filtros);
            case SAUDACAO_AJUDA -> respostaFormatter.mensagemAjuda();
            case NAO_ENTENDIDO -> respostaFormatter.mensagemNaoEntendido();
        };
    }

    // ---------------------------------------------------------------
    // CONSULTAR_PENDENCIAS
    // ---------------------------------------------------------------

    private String consultarPendencias(UUID empresaId, PreferenciaNotificacao pref, UUID lojaId, FiltrosAgente filtros) {
        PeriodoResolvido periodo = resolverPeriodo(filtros);

        List<Boleto> boletos = List.of();
        List<PagamentoPix> pixs = List.of();
        List<Cheque> cheques = List.of();

        TipoPagamentoAgente tipo = filtros.getTipoPagamento();

        if (tipo == null || tipo == TipoPagamentoAgente.BOLETO) {
            StatusBoleto status = (periodo.tipo() == TipoPeriodoAgente.VENCIDOS) ? null : StatusBoleto.PENDENTE;
            boletos = boletoRepository.findAll(BoletoSpecification.comFiltros(
                    empresaId, lojaId, status, periodo.de(), periodo.ate(), null));
            boletos = filtrarPorLojasPermitidas(boletos, pref, Boleto::getLoja);
        }
        if (tipo == null || tipo == TipoPagamentoAgente.PIX) {
            StatusPix status = (periodo.tipo() == TipoPeriodoAgente.VENCIDOS) ? null : StatusPix.PENDENTE;
            pixs = pixRepository.findAll(PagamentoPixSpecification.comFiltros(
                    empresaId, lojaId, status, periodo.de(), periodo.ate(), null));
            pixs = filtrarPorLojasPermitidas(pixs, pref, PagamentoPix::getLoja);
        }
        if (tipo == null || tipo == TipoPagamentoAgente.CHEQUE) {
            StatusCheque status = (periodo.tipo() == TipoPeriodoAgente.VENCIDOS) ? null : StatusCheque.PENDENTE;
            cheques = chequeRepository.findAll(ChequeSpecification.comFiltros(
                    empresaId, lojaId, status, periodo.de(), periodo.ate(), null));
            cheques = filtrarPorLojasPermitidas(cheques, pref, Cheque::getLoja);
        }

        // VENCIDOS: a specification já recebe [null, ontem], mas reforçamos em
        // memória que nenhum item PAGO/COMPENSADO/CANCELADO entre na lista —
        // status=null na specification significa "qualquer status".
        if (periodo.tipo() == TipoPeriodoAgente.VENCIDOS) {
            boletos = boletos.stream().filter(b -> b.getStatus() != StatusBoleto.PAGO && b.getStatus() != StatusBoleto.CANCELADO).toList();
            pixs = pixs.stream().filter(p -> p.getStatus() != StatusPix.PAGO && p.getStatus() != StatusPix.CANCELADO).toList();
            cheques = cheques.stream().filter(c -> c.getStatus() != StatusCheque.COMPENSADO && c.getStatus() != StatusCheque.CANCELADO).toList();
        }

        return respostaFormatter.formatarResumoPendencias(boletos, pixs, cheques, periodo.descricao());
    }

    // ---------------------------------------------------------------
    // OBTER_DADOS_PAGAMENTO (código de barras / chave PIX)
    // ---------------------------------------------------------------

    private String obterDadosPagamento(UUID empresaId, PreferenciaNotificacao pref, UUID lojaId, FiltrosAgente filtros) {
        PeriodoResolvido periodo = resolverPeriodo(filtros);
        TipoPagamentoAgente tipo = filtros.getTipoPagamento();

        List<Boleto> boletosCandidatos = List.of();
        List<PagamentoPix> pixCandidatos = List.of();

        if (tipo == null || tipo == TipoPagamentoAgente.BOLETO) {
            boletosCandidatos = boletoRepository.findAll(BoletoSpecification.comFiltros(
                    empresaId, lojaId, StatusBoleto.PENDENTE, periodo.de(), periodo.ate(), filtros.getFornecedor()));
            boletosCandidatos = filtrarPorLojasPermitidas(boletosCandidatos, pref, Boleto::getLoja);
        }
        if (tipo == null || tipo == TipoPagamentoAgente.PIX) {
            pixCandidatos = pixRepository.findAll(PagamentoPixSpecification.comFiltros(
                    empresaId, lojaId, StatusPix.PENDENTE, periodo.de(), periodo.ate(), filtros.getFornecedor()));
            pixCandidatos = filtrarPorLojasPermitidas(pixCandidatos, pref, PagamentoPix::getLoja);
        }

        int totalCandidatos = boletosCandidatos.size() + pixCandidatos.size();

        if (totalCandidatos == 0) {
            return "Não encontrei nenhum boleto ou PIX pendente com esses critérios.";
        }
        if (totalCandidatos > 1) {
            return respostaFormatter.formatarListaParaDesambiguar(boletosCandidatos, pixCandidatos);
        }

        if (!boletosCandidatos.isEmpty()) {
            return respostaFormatter.formatarDadosBoleto(boletosCandidatos.get(0));
        }
        return respostaFormatter.formatarDadosPix(pixCandidatos.get(0));
    }

    // ---------------------------------------------------------------
    // OBTER_DADOS_CHEQUE
    // ---------------------------------------------------------------

    private String obterDadosCheque(UUID empresaId, PreferenciaNotificacao pref, UUID lojaId, FiltrosAgente filtros) {
        PeriodoResolvido periodo = resolverPeriodo(filtros);

        List<Cheque> candidatos = chequeRepository.findAll(ChequeSpecification.comFiltros(
                empresaId, lojaId, null, periodo.de(), periodo.ate(), filtros.getFornecedor()));
        candidatos = filtrarPorLojasPermitidas(candidatos, pref, Cheque::getLoja);

        if (candidatos.isEmpty()) {
            return "Não encontrei nenhum cheque com esses critérios.";
        }
        if (candidatos.size() > 1) {
            return respostaFormatter.formatarListaChequesParaDesambiguar(candidatos);
        }

        Cheque cheque = candidatos.get(0);
        String respostaDados = respostaFormatter.formatarDadosCheque(cheque);

        // Nunca cancela aqui — só informa e orienta a ação correta.
        return respostaDados + "\n\nPara cancelar ou sustar este cheque, acesse o sistema ou fale com o financeiro. Por segurança, não fazemos esse tipo de alteração pelo WhatsApp.";
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Resolve o nome de loja citado pela LLM (texto livre) para um UUID
     * real, buscando entre as lojas da empresa do usuário. Busca
     * case-insensitive e por substring, igual ao padrão usado nas
     * Specifications para fornecedor.
     */
    private UUID resolverLoja(UUID empresaId, String nomeLoja) {
        if (nomeLoja == null || nomeLoja.isBlank()) {
            return null;
        }
        List<Loja> lojas = lojaRepository.findByEmpresaIdOrderByNome(empresaId);
        String termo = nomeLoja.trim().toLowerCase();

        return lojas.stream()
                .filter(l -> l.getNome().toLowerCase().contains(termo))
                .findFirst()
                .map(Loja::getId)
                .orElse(null);
    }

    /**
     * Garante que, mesmo filtrando no banco por empresaId, o resultado
     * final só contenha itens de lojas que o usuário tem permissão de ver
     * (mesma regra do {@code NotificacaoWhatsAppScheduler}: lojaIds vazio
     * = sem restrição adicional, igual ao comportamento já existente).
     */
    private <T> List<T> filtrarPorLojasPermitidas(List<T> itens, PreferenciaNotificacao pref, java.util.function.Function<T, Loja> lojaExtractor) {
        if (pref.getLojaIds().isEmpty()) {
            return itens;
        }
        return itens.stream()
                .filter(item -> pref.getLojaIds().contains(lojaExtractor.apply(item).getId()))
                .toList();
    }

    /**
     * Traduz {@link TipoPeriodoAgente} + datas opcionais (quando INTERVALO)
     * em um intervalo [de, ate] concreto, calculado em código — nunca pela
     * LLM — a partir da data corrente do servidor.
     */
    private PeriodoResolvido resolverPeriodo(FiltrosAgente filtros) {
        LocalDate hoje = LocalDate.now();
        TipoPeriodoAgente tipo = filtros.getPeriodo() != null ? filtros.getPeriodo() : TipoPeriodoAgente.SEM_FILTRO;

        return switch (tipo) {
            case HOJE -> new PeriodoResolvido(tipo, hoje, hoje, "hoje");
            case AMANHA -> new PeriodoResolvido(tipo, hoje.plusDays(1), hoje.plusDays(1), "amanhã");
            case SEMANA -> {
                LocalDate inicioSemana = hoje.with(DayOfWeek.MONDAY);
                LocalDate fimSemana = hoje.with(DayOfWeek.SUNDAY);
                yield new PeriodoResolvido(tipo, inicioSemana, fimSemana, "essa semana");
            }
            case VENCIDOS -> new PeriodoResolvido(tipo, null, hoje.minusDays(1), "em atraso");
            case INTERVALO -> {
                LocalDate de = filtros.getDataInicio() != null ? filtros.getDataInicio() : hoje;
                LocalDate ate = filtros.getDataFim() != null ? filtros.getDataFim() : hoje;
                yield new PeriodoResolvido(tipo, de, ate, "de " + de + " até " + ate);
            }
            case SEM_FILTRO -> new PeriodoResolvido(tipo, null, null, null);
        };
    }

    private String mascarar(String telefone) {
        if (telefone == null || telefone.length() < 4) {
            return "****";
        }
        return "****" + telefone.substring(telefone.length() - 4);
    }

    /** Intervalo de datas já resolvido, pronto para entrar nas Specifications. */
    private record PeriodoResolvido(TipoPeriodoAgente tipo, LocalDate de, LocalDate ate, String descricao) {}
}