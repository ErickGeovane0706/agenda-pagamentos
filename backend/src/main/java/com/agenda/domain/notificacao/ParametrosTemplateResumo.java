package com.agenda.domain.notificacao;

import java.math.BigDecimal;
import java.util.List;

/**
 * Extrai os 5 parâmetros do template "lembrete_resumo_pendencias"
 * aprovado na Meta ({{1}} a {{5}}).
 */
public class ParametrosTemplateResumo {

    public record Parametros(
            String nomeUsuario,     // {{1}}
            String qtdPendencias,   // {{2}}
            String qtdLojas,        // {{3}}
            String valorTotal,      // {{4}}
            String valorVencido     // {{5}}
    ) {
        public List<String> paraLista() {
            return List.of(nomeUsuario, qtdPendencias, qtdLojas, valorTotal, valorVencido);
        }
    }

    public Parametros extrair(String nomeUsuario,
                              List<PendenciaNotificacao> vencidos,
                              List<PendenciaNotificacao> venceHoje,
                              List<PendenciaNotificacao> venceFimDeSemana) {

        var todas = new java.util.ArrayList<PendenciaNotificacao>();
        todas.addAll(vencidos);
        todas.addAll(venceHoje);
        todas.addAll(venceFimDeSemana);

        BigDecimal totalGeral = todas.stream()
                .map(PendenciaNotificacao::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalVencido = vencidos.stream()
                .map(PendenciaNotificacao::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long qtdLojas = todas.stream()
                .map(PendenciaNotificacao::lojaNome)
                .distinct()
                .count();

        var fmt = java.util.Locale.of("pt", "BR");

        return new Parametros(
                nomeUsuario,
                String.valueOf(todas.size()),
                String.valueOf(qtdLojas),
                "R$ " + String.format(fmt, "%,.2f", totalGeral),
                "R$ " + String.format(fmt, "%,.2f", totalVencido)
        );
    }
}