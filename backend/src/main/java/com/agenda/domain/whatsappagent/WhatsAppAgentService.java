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
import com.agenda.security.RateLimiterService;
import com.agenda.whatsapp.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
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
    private final RateLimiterService rateLimiter;

    /**
     * Teto de mensagens por REMETENTE (telefone): 15 a cada 5 minutos.
     * Cada mensagem processada dispara uma chamada PAGA à Anthropic — este
     * teto evita que um único número (mesmo já cadastrado) amplifique o custo
     * de IA com um flood. Keado por telefone (infalsificável, dentro do
     * payload já validado por HMAC no webhook), não por IP: o webhook chega
     * todo dos IPs da Meta, então IP aqui não distingue remetentes.
     * <p>
     * Visibilidade de pacote (não {@code private}) porque o
     * {@link WhatsAppAudioService} consome o MESMO bucket, antes de pagar pela
     * transcrição — ver {@link #processarTranscricao}.
     */
    static final int WEBHOOK_MAX_MENSAGENS = 15;
    static final Duration WEBHOOK_JANELA = Duration.ofMinutes(5);

    /**
     * Processa uma mensagem de TEXTO recebida do webhook. Consome 1 token do
     * teto por remetente.
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
        if (!rateLimiter.tentarConsumir("wa:" + telefoneOrigem, WEBHOOK_MAX_MENSAGENS, WEBHOOK_JANELA)) {
            // Teto por remetente atingido: descarta em silêncio (sem chamar a IA
            // nem responder) para não amplificar custo nem entrar em loop de eco.
            log.warn("[WHATSAPP-AGENTE] Teto de mensagens por remetente atingido, ignorando: {}", mascarar(telefoneOrigem));
            return;
        }
        processar(telefoneOrigem, textoMensagem, false);
    }

    /**
     * Processa o TEXTO JÁ TRANSCRITO de uma mensagem de voz. Chamado pelo
     * {@link WhatsAppAudioService} depois de baixar e transcrever o áudio.
     * <p>
     * NÃO consome o teto por remetente de novo: o {@code WhatsAppAudioService}
     * já consumiu o token do mesmo bucket antes de pagar pela transcrição.
     * Cada mensagem — texto ou áudio — custa exatamente 1 token.
     * <p>
     * A resposta sai com a transcrição ecoada na frente: uma transcrição errada
     * faria o agente responder outra pergunta, e sem o eco o cliente não teria
     * como perceber o porquê.
     */
    @Async
    @Transactional(readOnly = true)
    public void processarTranscricao(String telefoneOrigem, String transcricao) {
        processar(telefoneOrigem, transcricao, true);
    }

    /**
     * Corpo compartilhado pelos dois fluxos (texto digitado e áudio já
     * transcrito). Não lança exceção — toda falha interna resulta numa mensagem
     * de erro amigável ao cliente, para nunca deixar o WhatsApp "no vácuo"
     * depois de uma pergunta.
     */
    private void processar(String telefoneOrigem, String textoMensagem, boolean origemAudio) {
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
            FiltrosAgente filtrosAnteriores = ultimaConsultaPorUsuario.get(pref.getUsuario().getId());
            ResultadoClassificacao resultado = classifierService.classificar(textoMensagem, filtrosAnteriores);
            log.info("[WHATSAPP-AGENTE] Usuário {} | intenção classificada: {}", pref.getUsuario().getId(), resultado.getIntencao());

            String resposta = rotear(empresaId, pref, resultado);
            if (origemAudio) {
                resposta = "🎧 Entendi: \"" + textoMensagem + "\"\n\n" + resposta;
            }
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
            case CONSULTAR_PENDENCIAS, REFINAR_ULTIMA_CONSULTA -> {
                // Gap J: se a mensagem só trouxe 1 filtro novo (ex.: "e da loja centro?"),
                // faz merge com a última consulta completa do usuário em vez de tratar
                // como pergunta nova do zero. Quando a própria LLM já classificou como
                // REFINAR_ULTIMA_CONSULTA, o merge é forçado independente de quantos
                // campos vieram preenchidos — a LLM já reconheceu que é continuação.
                // Precisa re-resolver a loja pois o merge pode ter trazido um nome de
                // loja diferente do que já foi resolvido acima.
                boolean refinamentoExplicito = resultado.getIntencao() == IntencaoAgente.REFINAR_ULTIMA_CONSULTA;
                FiltrosAgente filtrosMesclados = mesclarComUltimaConsulta(pref.getUsuario().getId(), filtros, refinamentoExplicito);
                UUID lojaIdMesclada = filtrosMesclados == filtros ? lojaId : resolverLoja(empresaId, filtrosMesclados.getLoja());
                yield consultarPendencias(empresaId, pref, lojaIdMesclada, filtrosMesclados);
            }
            case OBTER_DADOS_PAGAMENTO -> obterDadosPagamento(empresaId, pref, lojaId, filtros);
            case OBTER_DADOS_CHEQUE -> obterDadosCheque(empresaId, pref, lojaId, filtros);
            case DETALHAR_ULTIMA_CONSULTA -> detalharUltimaConsulta(empresaId, pref);
            case SAUDACAO_AJUDA -> respostaFormatter.mensagemAjuda();
            case NAO_ENTENDIDO -> respostaFormatter.mensagemNaoEntendido();
        };
    }

    // ---------------------------------------------------------------
    // CONSULTAR_PENDENCIAS
    // ---------------------------------------------------------------

    /** Acima desse total de itens, a resposta vira resumo por loja em vez de listar cada item. */
    private static final int LIMITE_ITENS_MODO_DETALHADO = 8;

    private String consultarPendencias(UUID empresaId, PreferenciaNotificacao pref, UUID lojaId, FiltrosAgente filtros) {
        return consultarPendencias(empresaId, pref, lojaId, filtros, false);
    }

    /**
     * @param forcarDetalhado {@code true} quando esta chamada vem de
     *        {@code DETALHAR_ULTIMA_CONSULTA} — força a lista completa
     *        mesmo que o volume normalmente cairia no modo resumido.
     */
    private String consultarPendencias(UUID empresaId, PreferenciaNotificacao pref, UUID lojaId, FiltrosAgente filtros, boolean forcarDetalhado) {
        PeriodoResolvido periodo = resolverPeriodo(filtros);
        FiltroStatusAgente filtroStatus = filtros.getFiltroStatus() != null ? filtros.getFiltroStatus() : FiltroStatusAgente.PENDENTE;
        // "incluirPagos" (nome antigo do campo) só existe agora como conceito derivado,
        // para o formatador saber se deve mostrar a coluna de status pago/pendente.
        boolean incluirPagos = filtroStatus != FiltroStatusAgente.PENDENTE;

        List<Boleto> boletos = List.of();
        List<PagamentoPix> pixs = List.of();
        List<Cheque> cheques = List.of();

        TipoPagamentoAgente tipo = filtros.getTipoPagamento();

        // status=null na specification significa "qualquer status" — buscamos tudo
        // sempre que precisamos filtrar em memória por PAGO ou por VENCIDOS, e só
        // usamos o status fixo do repositório quando o caso é o mais simples (PENDENTE).
        boolean precisaBuscarTudo = filtroStatus != FiltroStatusAgente.PENDENTE || periodo.tipo() == TipoPeriodoAgente.VENCIDOS;

        if (tipo == null || tipo == TipoPagamentoAgente.BOLETO) {
            StatusBoleto status = precisaBuscarTudo ? null : StatusBoleto.PENDENTE;
            boletos = boletoRepository.findAll(BoletoSpecification.comFiltros(
                    empresaId, lojaId, status, periodo.de(), periodo.ate(), null));
            boletos = filtrarPorLojasPermitidas(boletos, pref, Boleto::getLoja);
        }
        if (tipo == null || tipo == TipoPagamentoAgente.PIX) {
            StatusPix status = precisaBuscarTudo ? null : StatusPix.PENDENTE;
            pixs = pixRepository.findAll(PagamentoPixSpecification.comFiltros(
                    empresaId, lojaId, status, periodo.de(), periodo.ate(), null));
            pixs = filtrarPorLojasPermitidas(pixs, pref, PagamentoPix::getLoja);
        }
        if (tipo == null || tipo == TipoPagamentoAgente.CHEQUE) {
            StatusCheque status = precisaBuscarTudo ? null : StatusCheque.PENDENTE;
            cheques = chequeRepository.findAll(ChequeSpecification.comFiltros(
                    empresaId, lojaId, status, periodo.de(), periodo.ate(), null));
            cheques = filtrarPorLojasPermitidas(cheques, pref, Cheque::getLoja);
        }

        if (filtroStatus == FiltroStatusAgente.PAGO) {
            // Só o que já foi pago/compensado — exclui pendente e cancelado.
            boletos = boletos.stream().filter(b -> b.getStatus() == StatusBoleto.PAGO).toList();
            pixs = pixs.stream().filter(p -> p.getStatus() == StatusPix.PAGO).toList();
            cheques = cheques.stream().filter(c -> c.getStatus() == StatusCheque.COMPENSADO).toList();
        } else if (filtroStatus == FiltroStatusAgente.TODOS) {
            // Traz PAGO/COMPENSADO junto com PENDENTE — só CANCELADO fica fora,
            // já que um item cancelado não é "pago" nem "falta pagar".
            boletos = boletos.stream().filter(b -> b.getStatus() != StatusBoleto.CANCELADO).toList();
            pixs = pixs.stream().filter(p -> p.getStatus() != StatusPix.CANCELADO).toList();
            cheques = cheques.stream().filter(c -> c.getStatus() != StatusCheque.CANCELADO).toList();
        } else if (periodo.tipo() == TipoPeriodoAgente.VENCIDOS) {
            // VENCIDOS + PENDENTE: já buscamos tudo acima, aqui filtramos em memória
            // pra excluir PAGO/COMPENSADO/CANCELADO.
            boletos = boletos.stream().filter(b -> b.getStatus() != StatusBoleto.PAGO && b.getStatus() != StatusBoleto.CANCELADO).toList();
            pixs = pixs.stream().filter(p -> p.getStatus() != StatusPix.PAGO && p.getStatus() != StatusPix.CANCELADO).toList();
            cheques = cheques.stream().filter(c -> c.getStatus() != StatusCheque.COMPENSADO && c.getStatus() != StatusCheque.CANCELADO).toList();
        }

        int totalItens = boletos.size() + pixs.size() + cheques.size();
        boolean modoResumido = !forcarDetalhado && totalItens > LIMITE_ITENS_MODO_DETALHADO;

        // Guarda os filtros desta consulta (bem-sucedida) para uma eventual mensagem
        // curta de refinamento logo em seguida ("e da loja centro?") poder herdar
        // o restante dos filtros — ver mesclarComUltimaConsulta.
        guardarUltimaConsulta(pref.getUsuario().getId(), filtros);

        if (modoResumido) {
            // Guarda também para o próximo "manda detalhado" poder reexecutar
            // exatamente a mesma busca, sem o cliente precisar repetir tudo.
            guardarUltimaConsultaResumida(pref.getUsuario().getId(), filtros);
            return respostaFormatter.formatarResumoPorLoja(boletos, pixs, cheques, periodo.descricao(), incluirPagos);
        }

        limparUltimaConsultaResumida(pref.getUsuario().getId());

        // "Detalhar" pode ter sido chamado sobre um resumo com muitos itens —
        // uma lista completa de, digamos, 143 itens ultrapassaria o limite de
        // 4096 caracteres de uma mensagem do WhatsApp. Nesse caso, mostramos
        // só os itens com vencimento mais próximo (os mais urgentes de agir)
        // e avisamos quantos ficaram de fora, sugerindo filtrar por loja.
        if (totalItens > LIMITE_ITENS_DETALHE_COMPLETO) {
            TrioTruncado truncado = truncarPorVencimentoMaisProximo(boletos, pixs, cheques, LIMITE_ITENS_DETALHE_COMPLETO);
            return respostaFormatter.formatarListaCompleta(
                    truncado.boletos(), truncado.pixs(), truncado.cheques(),
                    periodo.descricao(), incluirPagos, totalItens);
        }

        return respostaFormatter.formatarListaCompleta(boletos, pixs, cheques, periodo.descricao(), incluirPagos, null);
    }

    /** Acima desse total de itens, mesmo o modo detalhado é truncado (limite de caracteres do WhatsApp). */
    private static final int LIMITE_ITENS_DETALHE_COMPLETO = 40;

    /** Resultado de {@link #truncarPorVencimentoMaisProximo}: as três listas já reduzidas ao limite. */
    private record TrioTruncado(List<Boleto> boletos, List<PagamentoPix> pixs, List<Cheque> cheques) {}

    /**
     * Seleciona, entre boletos/PIX/cheques combinados, os {@code limite}
     * itens com vencimento mais próximo (os mais urgentes de agir), mesmo
     * misturando os três tipos. Preserva os objetos originais — não altera
     * nenhum valor, só decide quais entram na resposta.
     */
    private TrioTruncado truncarPorVencimentoMaisProximo(List<Boleto> boletos, List<PagamentoPix> pixs, List<Cheque> cheques, int limite) {
        record Marcado(int tipo, int indice, LocalDate vencimento) {}

        List<Marcado> marcados = new java.util.ArrayList<>();
        for (int i = 0; i < boletos.size(); i++) marcados.add(new Marcado(0, i, boletos.get(i).getVencimento()));
        for (int i = 0; i < pixs.size(); i++) marcados.add(new Marcado(1, i, pixs.get(i).getVencimento()));
        for (int i = 0; i < cheques.size(); i++) marcados.add(new Marcado(2, i, cheques.get(i).getVencimento()));

        java.util.Set<Integer> boletosSelecionados = new java.util.HashSet<>();
        java.util.Set<Integer> pixsSelecionados = new java.util.HashSet<>();
        java.util.Set<Integer> chequesSelecionados = new java.util.HashSet<>();

        marcados.stream()
                .sorted(java.util.Comparator.comparing(Marcado::vencimento))
                .limit(limite)
                .forEach(m -> {
                    switch (m.tipo()) {
                        case 0 -> boletosSelecionados.add(m.indice());
                        case 1 -> pixsSelecionados.add(m.indice());
                        case 2 -> chequesSelecionados.add(m.indice());
                    }
                });

        List<Boleto> boletosTruncados = java.util.stream.IntStream.range(0, boletos.size())
                .filter(boletosSelecionados::contains).mapToObj(boletos::get).toList();
        List<PagamentoPix> pixsTruncados = java.util.stream.IntStream.range(0, pixs.size())
                .filter(pixsSelecionados::contains).mapToObj(pixs::get).toList();
        List<Cheque> chequesTruncados = java.util.stream.IntStream.range(0, cheques.size())
                .filter(chequesSelecionados::contains).mapToObj(cheques::get).toList();

        return new TrioTruncado(boletosTruncados, pixsTruncados, chequesTruncados);
    }

    // ---------------------------------------------------------------
    // DETALHAR_ULTIMA_CONSULTA
    // ---------------------------------------------------------------

    /**
     * Cache em memória (não persiste em banco, não sobrevive a restart do
     * serviço) da última consulta respondida em modo resumido, por usuário.
     * Suficiente para o caso de uso: "detalhar" só faz sentido como resposta
     * imediata à mensagem anterior — se o serviço reiniciar ou o cliente
     * voltar dias depois, ele simplesmente recebe o aviso de que não há
     * consulta recente para detalhar, e pode perguntar de novo.
     */
    private final java.util.Map<UUID, FiltrosAgente> ultimaConsultaResumidaPorUsuario = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Detalhe já formatado (texto livre) de um lembrete automático recente,
     * por usuário. O {@code NotificacaoWhatsAppScheduler} envia proativamente
     * só o template de resumo e guarda AQUI a lista item a item — que só é
     * mandada (dentro da janela de 24h) se o cliente responder "detalhar".
     * <p>
     * Diferente do fluxo de chat, o detalhe do lembrete é CONGELADO no momento
     * do disparo (não re-consultado por filtros), porque o lembrete é a união
     * de vencidos + vence hoje + fim de semana — combinação que não se reduz a
     * um único {@link FiltrosAgente}. Congelar garante que o "detalhar" bata
     * exatamente com o resumo que o cliente recebeu.
     */
    private final java.util.Map<UUID, String> detalheNotificacaoPorUsuario = new java.util.concurrent.ConcurrentHashMap<>();

    private void guardarUltimaConsultaResumida(UUID usuarioId, FiltrosAgente filtros) {
        ultimaConsultaResumidaPorUsuario.put(usuarioId, filtros);
        // Uma consulta resumida via chat passa a ser a "última ação" a detalhar:
        // invalida qualquer detalhe de lembrete pendente (comportamento "último vence").
        detalheNotificacaoPorUsuario.remove(usuarioId);
    }

    private void limparUltimaConsultaResumida(UUID usuarioId) {
        ultimaConsultaResumidaPorUsuario.remove(usuarioId);
        detalheNotificacaoPorUsuario.remove(usuarioId);
    }

    /**
     * Registra, para um usuário, o detalhe já formatado de um lembrete
     * automático, para que um "detalhar" logo em seguida devolva exatamente
     * essa lista. Chamado pelo {@code NotificacaoWhatsAppScheduler}.
     * <p>
     * Como esta é a "última ação" a detalhar, invalida qualquer consulta
     * resumida de chat pendente — assim, no máximo uma das duas fontes fica
     * ativa por vez e o "detalhar" nunca fica ambíguo.
     */
    public void registrarDetalheNotificacao(UUID usuarioId, String detalhe) {
        detalheNotificacaoPorUsuario.put(usuarioId, detalhe);
        ultimaConsultaResumidaPorUsuario.remove(usuarioId);
    }

    /**
     * Cache separado da última CONSULTAR_PENDENCIAS bem-sucedida, guardado
     * em TODA consulta (não só quando cai em modo resumido) — usado só para
     * merge de refinamento (gap J), nunca para "detalhar". Mesma limitação
     * de sempre: não sobrevive a restart do serviço nem persiste em banco.
     */
    private final java.util.Map<UUID, FiltrosAgente> ultimaConsultaPorUsuario = new java.util.concurrent.ConcurrentHashMap<>();

    private void guardarUltimaConsulta(UUID usuarioId, FiltrosAgente filtros) {
        ultimaConsultaPorUsuario.put(usuarioId, filtros);
    }

    /**
     * Se a mensagem nova só especificou 1 filtro (ex.: só loja, como em
     * "e da loja centro?") e existe uma consulta anterior guardada para o
     * usuário, preenche os campos que a mensagem nova deixou em aberto
     * (SEM_FILTRO / null) com os valores da consulta anterior — sem nunca
     * sobrescrever um campo que a mensagem nova preencheu explicitamente.
     * Mensagens já completas (2+ filtros específicos, ou 0 filtros — ex.:
     * "quanto tenho pra pagar" pura) não disparam merge: presumimos que o
     * cliente quis uma pergunta nova e genérica, não um refinamento.
     *
     * @param forcarMerge quando {@code true} (intenção já classificada como
     *        {@code REFINAR_ULTIMA_CONSULTA}), ignora a contagem de campos
     *        e sempre tenta mesclar — a LLM já decidiu que é continuação.
     */
    private FiltrosAgente mesclarComUltimaConsulta(UUID usuarioId, FiltrosAgente novos, boolean forcarMerge) {
        if (!forcarMerge) {
            int camposPreenchidos = 0;
            if (novos.getLoja() != null && !novos.getLoja().isBlank()) camposPreenchidos++;
            if (novos.getTipoPagamento() != null) camposPreenchidos++;
            if (novos.getPeriodo() != null && novos.getPeriodo() != TipoPeriodoAgente.SEM_FILTRO) camposPreenchidos++;
            if (novos.getFornecedor() != null && !novos.getFornecedor().isBlank()) camposPreenchidos++;

            if (camposPreenchidos != 1) {
                return novos;
            }
        }

        FiltrosAgente anteriores = ultimaConsultaPorUsuario.get(usuarioId);
        if (anteriores == null) {
            return novos;
        }

        // Campos de período "composto" (diaSemanaAlvo, posicaoQuinzena, diaDoMes,
        // mesReferencia) só fazem sentido junto do periodo que os originou — por
        // isso viajam JUNTOS com o periodo escolhido (novo OU anterior), nunca
        // misturados entre um filtro novo e o outro antigo.
        boolean usaPeriodoNovo = novos.getPeriodo() != null && novos.getPeriodo() != TipoPeriodoAgente.SEM_FILTRO;
        FiltrosAgente origemPeriodo = usaPeriodoNovo ? novos : anteriores;

        return FiltrosAgente.builder()
                .loja(novos.getLoja() != null && !novos.getLoja().isBlank() ? novos.getLoja() : anteriores.getLoja())
                .tipoPagamento(novos.getTipoPagamento() != null ? novos.getTipoPagamento() : anteriores.getTipoPagamento())
                .periodo(usaPeriodoNovo ? novos.getPeriodo() : anteriores.getPeriodo())
                .diaSemanaAlvo(origemPeriodo.getDiaSemanaAlvo())
                .posicaoQuinzena(origemPeriodo.getPosicaoQuinzena())
                .diaDoMes(origemPeriodo.getDiaDoMes())
                .mesReferencia(origemPeriodo.getMesReferencia())
                .dataInicio(usaPeriodoNovo ? novos.getDataInicio() : anteriores.getDataInicio())
                .dataFim(usaPeriodoNovo ? novos.getDataFim() : anteriores.getDataFim())
                .fornecedor(novos.getFornecedor() != null && !novos.getFornecedor().isBlank() ? novos.getFornecedor() : anteriores.getFornecedor())
                .filtroStatus(novos.getFiltroStatus() != null ? novos.getFiltroStatus() : anteriores.getFiltroStatus())
                .build();
    }

    private String detalharUltimaConsulta(UUID empresaId, PreferenciaNotificacao pref) {
        UUID usuarioId = pref.getUsuario().getId();

        // Detalhe congelado de um lembrete automático recente tem prioridade:
        // reproduz item a item exatamente o que o lembrete resumiu, sem
        // re-consultar o banco (ver detalheNotificacaoPorUsuario).
        String detalheLembrete = detalheNotificacaoPorUsuario.remove(usuarioId);
        if (detalheLembrete != null) {
            return detalheLembrete;
        }

        FiltrosAgente filtrosAnteriores = ultimaConsultaResumidaPorUsuario.get(usuarioId);
        if (filtrosAnteriores == null) {
            return "Não tenho nenhum resumo recente pra detalhar. Pode fazer a pergunta novamente? Ex.: \"quanto tenho pra pagar essa semana\".";
        }

        UUID lojaId = resolverLoja(empresaId, filtrosAnteriores.getLoja());
        String resposta = consultarPendencias(empresaId, pref, lojaId, filtrosAnteriores, true);
        limparUltimaConsultaResumida(usuarioId);
        return resposta;
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
            case MES -> {
                LocalDate inicioMes = hoje.withDayOfMonth(1);
                LocalDate fimMes = hoje.withDayOfMonth(hoje.lengthOfMonth());
                yield new PeriodoResolvido(tipo, inicioMes, fimMes, "esse mês");
            }
            case ANO -> {
                LocalDate inicioAno = hoje.withDayOfYear(1);
                LocalDate fimAno = hoje.withDayOfYear(hoje.lengthOfYear());
                yield new PeriodoResolvido(tipo, inicioAno, fimAno, "esse ano");
            }
            case VENCIDOS -> new PeriodoResolvido(tipo, null, hoje.minusDays(1), "em atraso");
            case ONTEM -> new PeriodoResolvido(tipo, hoje.minusDays(1), hoje.minusDays(1), "ontem");
            case SEMANA_PASSADA -> {
                LocalDate inicioSemanaAtual = hoje.with(DayOfWeek.MONDAY);
                LocalDate inicio = inicioSemanaAtual.minusWeeks(1);
                LocalDate fim = inicioSemanaAtual.minusDays(1);
                yield new PeriodoResolvido(tipo, inicio, fim, "semana passada");
            }
            case PROXIMA_SEMANA -> {
                LocalDate inicioSemanaAtual = hoje.with(DayOfWeek.MONDAY);
                LocalDate inicio = inicioSemanaAtual.plusWeeks(1);
                LocalDate fim = inicio.with(DayOfWeek.SUNDAY);
                yield new PeriodoResolvido(tipo, inicio, fim, "semana que vem");
            }
            case MES_PASSADO -> {
                LocalDate primeiroDiaMesAtual = hoje.withDayOfMonth(1);
                LocalDate inicio = primeiroDiaMesAtual.minusMonths(1);
                LocalDate fim = primeiroDiaMesAtual.minusDays(1);
                yield new PeriodoResolvido(tipo, inicio, fim, "mês passado");
            }
            case PROXIMO_MES -> {
                LocalDate inicio = hoje.withDayOfMonth(1).plusMonths(1);
                LocalDate fim = inicio.withDayOfMonth(inicio.lengthOfMonth());
                yield new PeriodoResolvido(tipo, inicio, fim, "mês que vem");
            }
            case INTERVALO -> {
                LocalDate de = filtros.getDataInicio() != null ? filtros.getDataInicio() : hoje;
                LocalDate ate = filtros.getDataFim() != null ? filtros.getDataFim() : hoje;
                yield new PeriodoResolvido(tipo, de, ate, "de " + de + " até " + ate);
            }

            // Corrige o bug original: "até segunda" não tinha enum próprio e
            // caía em PROXIMA_SEMANA por aproximação, gerando um intervalo uma
            // semana inteira maior do que o esperado. Aqui o intervalo vai de
            // hoje até a PRÓXIMA ocorrência do dia da semana pedido (inclusive
            // hoje, se hoje já for esse dia).
            case ATE_DIA_SEMANA -> {
                if (filtros.getDiaSemanaAlvo() == null) {
                    yield new PeriodoResolvido(TipoPeriodoAgente.SEM_FILTRO, null, null, null);
                }
                DayOfWeek alvo = converterDiaSemana(filtros.getDiaSemanaAlvo());
                int diasAte = (alvo.getValue() - hoje.getDayOfWeek().getValue() + 7) % 7;
                LocalDate fim = hoje.plusDays(diasAte);
                yield new PeriodoResolvido(tipo, hoje, fim, "até " + descreverDiaSemana(filtros.getDiaSemanaAlvo()));
            }

            case QUINZENA -> {
                LocalDate mesRef = primeiroDiaDoMesReferencia(hoje, filtros.getMesReferencia());
                LocalDate inicio;
                LocalDate fim;
                if (filtros.getPosicaoQuinzena() == PosicaoQuinzenaAgente.PRIMEIRA) {
                    inicio = mesRef.withDayOfMonth(1);
                    fim = mesRef.withDayOfMonth(15);
                } else if (filtros.getPosicaoQuinzena() == PosicaoQuinzenaAgente.SEGUNDA) {
                    inicio = mesRef.withDayOfMonth(16);
                    fim = mesRef.withDayOfMonth(mesRef.lengthOfMonth());
                } else {
                    // Sem posição explícita: quinzena corrente com base no dia de hoje.
                    if (hoje.getDayOfMonth() <= 15) {
                        inicio = hoje.withDayOfMonth(1);
                        fim = hoje.withDayOfMonth(15);
                    } else {
                        inicio = hoje.withDayOfMonth(16);
                        fim = hoje.withDayOfMonth(hoje.lengthOfMonth());
                    }
                }
                yield new PeriodoResolvido(tipo, inicio, fim, "quinzena");
            }

            case FIM_DE_SEMANA -> {
                LocalDate sabado = hoje.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
                LocalDate domingo = sabado.plusDays(1);
                yield new PeriodoResolvido(tipo, sabado, domingo, "fim de semana");
            }

            case PROXIMO_FIM_DE_SEMANA -> {
                LocalDate sabadoEstaSemana = hoje.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
                LocalDate sabado = sabadoEstaSemana.plusWeeks(1);
                LocalDate domingo = sabado.plusDays(1);
                yield new PeriodoResolvido(tipo, sabado, domingo, "fim de semana que vem");
            }

            case INICIO_MES -> {
                LocalDate mesRef = primeiroDiaDoMesReferencia(hoje, filtros.getMesReferencia());
                yield new PeriodoResolvido(tipo, mesRef.withDayOfMonth(1), mesRef.withDayOfMonth(10), "início do mês");
            }

            case MEIO_MES -> {
                LocalDate mesRef = primeiroDiaDoMesReferencia(hoje, filtros.getMesReferencia());
                yield new PeriodoResolvido(tipo, mesRef.withDayOfMonth(11), mesRef.withDayOfMonth(20), "meio do mês");
            }

            case FIM_MES -> {
                LocalDate mesRef = primeiroDiaDoMesReferencia(hoje, filtros.getMesReferencia());
                yield new PeriodoResolvido(tipo, mesRef.withDayOfMonth(21), mesRef.withDayOfMonth(mesRef.lengthOfMonth()), "final do mês");
            }

            // Semana comercial (segunda a sexta) que atravessa a fronteira entre
            // o mês de referência e o seguinte: última segunda-feira do mês de
            // referência cuja sexta-feira correspondente já cai no mês seguinte.
            case VIRADA_MES -> {
                LocalDate mesRef = primeiroDiaDoMesReferencia(hoje, filtros.getMesReferencia());
                LocalDate ultimoDiaMes = mesRef.withDayOfMonth(mesRef.lengthOfMonth());
                LocalDate segunda = ultimoDiaMes.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate sexta = segunda.plusDays(4);
                // Se a sexta correspondente ainda cai dentro do próprio mês de
                // referência, a "semana de virada" real é a segunda seguinte.
                if (!sexta.isAfter(ultimoDiaMes)) {
                    segunda = segunda.plusWeeks(1);
                    sexta = segunda.plusDays(4);
                }
                yield new PeriodoResolvido(tipo, segunda, sexta, "virada do mês");
            }

            case DIA_DO_MES -> {
                LocalDate mesRef = primeiroDiaDoMesReferencia(hoje, filtros.getMesReferencia());
                int dia = filtros.getDiaDoMes() != null
                        ? Math.min(filtros.getDiaDoMes(), mesRef.lengthOfMonth())
                        : mesRef.lengthOfMonth();
                LocalDate alvo = mesRef.withDayOfMonth(dia);
                LocalDate inicio = alvo.isBefore(hoje) ? alvo : hoje;
                yield new PeriodoResolvido(tipo, inicio, alvo, "até o dia " + dia);
            }

            case SEM_FILTRO -> new PeriodoResolvido(tipo, null, null, null);
        };
    }

    /**
     * Resolve {@link MesReferenciaAgente} (ATUAL/PASSADO/PROXIMO, default
     * ATUAL quando null) para o primeiro dia do mês correspondente,
     * relativo a hoje.
     */
    private LocalDate primeiroDiaDoMesReferencia(LocalDate hoje, MesReferenciaAgente mesReferencia) {
        LocalDate primeiroDiaMesAtual = hoje.withDayOfMonth(1);
        if (mesReferencia == null) {
            return primeiroDiaMesAtual;
        }
        return switch (mesReferencia) {
            case PASSADO -> primeiroDiaMesAtual.minusMonths(1);
            case PROXIMO -> primeiroDiaMesAtual.plusMonths(1);
            case ATUAL -> primeiroDiaMesAtual;
        };
    }

    private DayOfWeek converterDiaSemana(DiaSemanaAgente dia) {
        return switch (dia) {
            case SEGUNDA -> DayOfWeek.MONDAY;
            case TERCA -> DayOfWeek.TUESDAY;
            case QUARTA -> DayOfWeek.WEDNESDAY;
            case QUINTA -> DayOfWeek.THURSDAY;
            case SEXTA -> DayOfWeek.FRIDAY;
            case SABADO -> DayOfWeek.SATURDAY;
            case DOMINGO -> DayOfWeek.SUNDAY;
        };
    }

    private String descreverDiaSemana(DiaSemanaAgente dia) {
        return switch (dia) {
            case SEGUNDA -> "segunda-feira";
            case TERCA -> "terça-feira";
            case QUARTA -> "quarta-feira";
            case QUINTA -> "quinta-feira";
            case SEXTA -> "sexta-feira";
            case SABADO -> "sábado";
            case DOMINGO -> "domingo";
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