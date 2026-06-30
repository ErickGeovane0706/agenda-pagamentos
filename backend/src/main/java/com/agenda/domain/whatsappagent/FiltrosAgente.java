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
}
