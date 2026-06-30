package com.agenda.domain.whatsappagent;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resultado da classificação de uma mensagem do cliente: a intenção e os
 * filtros associados. É exatamente o JSON que a LLM devolve, desserializado
 * pelo {@link IntentClassifierService} — ver formato completo no prompt em
 * {@code resources/prompts/classificador-whatsapp.txt}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResultadoClassificacao {

    private IntencaoAgente intencao;

    @Builder.Default
    private FiltrosAgente filtros = FiltrosAgente.builder().periodo(TipoPeriodoAgente.SEM_FILTRO).build();

    /**
     * Resultado de fallback usado quando a chamada à LLM falha (timeout,
     * erro HTTP, JSON inválido) — em vez de propagar exceção e nunca
     * responder o cliente, o agente cai aqui e manda uma mensagem de
     * erro amigável pedindo para reformular ou tentar mais tarde.
     */
    public static ResultadoClassificacao naoEntendido() {
        return ResultadoClassificacao.builder()
                .intencao(IntencaoAgente.NAO_ENTENDIDO)
                .filtros(FiltrosAgente.builder().periodo(TipoPeriodoAgente.SEM_FILTRO).build())
                .build();
    }
}
