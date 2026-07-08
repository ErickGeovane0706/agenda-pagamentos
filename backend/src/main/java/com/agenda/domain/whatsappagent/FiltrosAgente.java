package com.agenda.domain.whatsappagent;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Filtros que a LLM extraiu do texto livre do cliente. Todo campo é
 * opcional — a LLM preenche só o que a frase realmente mencionou e deixa
 * o resto {@code null}. Quem decide o que fazer com um campo nulo é o
 * {@link WhatsAppAgentService}, nunca a própria LLM.
 * <p>
 * Este é o "contrato" estruturado de saída do {@link IntentClassifierService}:
 * o prompt do classificador instrui a LLM a responder EXATAMENTE neste
 * formato JSON (ver {@code prompt-classificador.txt}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FiltrosAgente {

    /**
     * Nome (ou parte do nome) da loja mencionada, ex.: "Loja Centro".
     * Resolvido para UUID em {@link WhatsAppAgentService#resolverLoja}
     * por busca textual entre as lojas da empresa do usuário — a LLM
     * nunca recebe nem inventa UUIDs.
     */
    private String loja;

    /** Tipo de pagamento mencionado, ou {@code null} se não especificado (= todos). */
    private TipoPagamentoAgente tipoPagamento;

    /** Como o período foi delimitado. Nunca nulo na resposta da LLM — usa SEM_FILTRO. */
    private TipoPeriodoAgente periodo;

    /**
     * Dia da semana mencionado como limite ("até segunda"). Preenchido
     * apenas quando {@code periodo == ATE_DIA_SEMANA}; obrigatório nesse
     * caso. Ver {@link DiaSemanaAgente}.
     */
    private DiaSemanaAgente diaSemanaAlvo;

    /**
     * Qual metade do mês, quando o cliente especifica ("primeira quinzena",
     * "segunda quinzena"). Usado apenas quando {@code periodo == QUINZENA};
     * pode ficar {@code null} nesse mesmo caso se o cliente não especificar
     * ("a quinzena", sem qualificar) — o código assume a quinzena corrente.
     * Ver {@link PosicaoQuinzenaAgente}.
     */
    private PosicaoQuinzenaAgente posicaoQuinzena;

    /**
     * Dia numérico do mês mencionado ("até o dia 15"). Preenchido apenas
     * quando {@code periodo == DIA_DO_MES}; obrigatório nesse caso.
     * Valores válidos: 1 a 31 (dias inexistentes no mês de referência,
     * ex.: 31 em fevereiro, são truncados em código para o último dia real).
     */
    private Integer diaDoMes;

    /**
     * A qual mês o cliente se refere quando o período depende de "mês de
     * referência" (ver {@link MesReferenciaAgente}). Usado junto de
     * INICIO_MES, MEIO_MES, FIM_MES, VIRADA_MES, DIA_DO_MES e QUINZENA
     * (quando {@code posicaoQuinzena} está preenchida). Quando a LLM não
     * preenche e o período exige, o código assume ATUAL como padrão.
     */
    private MesReferenciaAgente mesReferencia;

    /** Preenchido apenas quando {@code periodo == INTERVALO}. */
    private LocalDate dataInicio;

    /** Preenchido apenas quando {@code periodo == INTERVALO}. */
    private LocalDate dataFim;

    /**
     * Nome (ou parte do nome) do fornecedor mencionado, usado para
     * desambiguar em {@code OBTER_DADOS_PAGAMENTO} / {@code OBTER_DADOS_CHEQUE}
     * quando há mais de uma pendência do mesmo tipo/período.
     */
    private String fornecedor;

    /**
     * Que status o cliente quer ver. Nunca nulo na resposta da LLM — usa
     * PENDENTE como padrão (comportamento de sempre: só o que falta pagar).
     * Ver {@link FiltroStatusAgente}.
     */
    private FiltroStatusAgente filtroStatus;
}