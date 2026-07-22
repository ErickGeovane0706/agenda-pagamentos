package com.agenda.domain.uso;

import com.agenda.domain.assinatura.AssinaturaRepository;
import com.agenda.domain.assinatura.StatusAssinatura;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.UUID;

/**
 * Teto de consumo pago por empresa.
 * <p>
 * Motivo de existir: o trial libera três recursos que custam dinheiro a
 * fornecedores externos — lembrete (template da Meta), agente (Anthropic) e
 * transcrição de voz (OpenAI) — e o cadastro público deixa qualquer um abrir um
 * trial confirmando um email. Sem teto por empresa, cada tenant falso vira uma
 * fatura sem limite superior.
 * <p>
 * <b>Não substitui o {@code RateLimiterService}, e o contrário também não.</b>
 * Aquele é por telefone, em memória, em janela de minutos: protege contra flood.
 * Este é por empresa, no banco, em janela de mês: protege contra custo. Um
 * tenant com dez telefones passa folgado no primeiro e é contido por este.
 * <p>
 * Serviço separado do {@code AssinaturaService} de propósito: aquele já é o hub
 * que todo mundo injeta (filtro HTTP, agente, scheduler) e seu javadoc declara
 * que existem exatamente duas checagens de acesso no sistema. Medição de consumo
 * é outra responsabilidade e tem outro ciclo de vida.
 */
@Service
public class LimiteUsoService {

    /**
     * Telefones que podem receber lembrete, por empresa: 2 na primeira loja e
     * +1 a cada loja adicional ({@code lojasContratadas + 1}). Amarra o custo
     * recorrente ao que a empresa paga, já que o preço é por loja.
     */
    private static final int DESTINATARIOS_ALEM_DAS_LOJAS = 1;

    /** O aviso de limite sai uma única vez por mês — daí o teto 1. */
    private static final int TETO_AVISO = 1;

    private final UsoMensalEmpresaRepository usoRepository;
    private final AssinaturaRepository assinaturaRepository;

    private final Tetos trial;
    private final Tetos ativa;

    public LimiteUsoService(UsoMensalEmpresaRepository usoRepository,
                            AssinaturaRepository assinaturaRepository,
                            @Value("${uso.trial.templates}") int trialTemplates,
                            @Value("${uso.trial.agente}") int trialAgente,
                            @Value("${uso.trial.audios}") int trialAudios,
                            @Value("${uso.ativa.templates}") int ativaTemplates,
                            @Value("${uso.ativa.agente}") int ativaAgente,
                            @Value("${uso.ativa.audios}") int ativaAudios) {
        this.usoRepository = usoRepository;
        this.assinaturaRepository = assinaturaRepository;
        this.trial = new Tetos(trialTemplates, trialAgente, trialAudios);
        this.ativa = new Tetos(ativaTemplates, ativaAgente, ativaAudios);
    }

    /**
     * Registra o consumo de uma unidade e diz se o chamador pode gastar.
     * Retorna {@code false} quando o teto do mês já foi atingido — nesse caso
     * nada é cobrado do fornecedor porque a checagem vem ANTES da chamada paga.
     * <p>
     * <b>{@code REQUIRES_NEW} é obrigatório, não preferência.</b> O
     * {@code NotificacaoWhatsAppScheduler} chama isto de dentro de uma transação
     * {@code readOnly}, e o Postgres recusa INSERT numa conexão marcada como
     * somente-leitura. A transação nova é curtíssima (um único statement) e
     * fecha antes de qualquer chamada HTTP.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean consumir(UUID empresaId, TipoUso tipo) {
        return usoRepository.consumirSeAbaixoDoTeto(
                empresaId, competenciaAtual(), tipo.name(), tetoDe(empresaId, tipo)) == 1;
    }

    /**
     * Diz se ESTA chamada é a que deve avisar a empresa de que o limite acabou,
     * e marca o aviso como dado. Verdadeiro no máximo uma vez por mês.
     * <p>
     * Sem isso, um cliente que insistisse depois de estourar a quota receberia
     * uma resposta por mensagem — o loop de eco que o agente já evita ao
     * descartar em silêncio quando o teto por telefone estoura.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deveAvisar(UUID empresaId) {
        return usoRepository.consumirSeAbaixoDoTeto(
                empresaId, competenciaAtual(), TipoUso.AVISO_LIMITE.name(), TETO_AVISO) == 1;
    }

    /**
     * Quantos telefones a empresa pode manter recebendo lembrete.
     * Empresa sem assinatura fica em zero: o seguro é bloquear, mesma postura
     * do {@code podeAcessar}.
     */
    @Transactional(readOnly = true)
    public int limiteDestinatarios(UUID empresaId) {
        return assinaturaRepository.findByEmpresaId(empresaId)
                .map(a -> a.getLojasContratadas() + DESTINATARIOS_ALEM_DAS_LOJAS)
                .orElse(0);
    }

    /**
     * Só quem está ATIVA (pagando) recebe o teto folgado. TRIAL — e o caso que
     * não deveria existir, empresa sem assinatura — cai no teto apertado, que é
     * onde mora o risco de abuso.
     */
    private int tetoDe(UUID empresaId, TipoUso tipo) {
        Tetos tetos = assinaturaRepository.findByEmpresaId(empresaId)
                .filter(a -> a.getStatus() == StatusAssinatura.ATIVA)
                .map(a -> ativa)
                .orElse(trial);
        return tetos.para(tipo);
    }

    private String competenciaAtual() {
        return YearMonth.now().toString();
    }

    private record Tetos(int templates, int agente, int audios) {
        int para(TipoUso tipo) {
            return switch (tipo) {
                case TEMPLATE -> templates;
                case AGENTE -> agente;
                case AUDIO -> audios;
                case AVISO_LIMITE -> TETO_AVISO;
            };
        }
    }
}
