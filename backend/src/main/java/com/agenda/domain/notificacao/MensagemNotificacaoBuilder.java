package com.agenda.domain.notificacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Monta os PARÂMETROS dos templates de WhatsApp a partir das pendências já
 * filtradas (vencidas, vencendo hoje, vencendo no fim de semana).
 * <p>
 * Importante: isto não monta mais um texto livre — desde que as mensagens
 * são iniciadas pela empresa, a Meta só aceita templates pré-aprovados, com
 * conteúdo fixo + variáveis posicionais ({{1}}, {{2}}...). As variáveis não
 * podem conter quebra de linha, então não dá pra simular aqui a lista
 * "🔴 VENCIDOS / 🟡 VENCE HOJE / 🔵 FIM DE SEMANA" de antes; em vez disso,
 * o scheduler dispara uma mensagem de resumo (este builder monta os valores
 * de {@link #parametrosResumo}) seguida de uma mensagem por pendência
 * individual ({@link #parametrosItem}), na ordem de prioridade vencidos →
 * hoje → fim de semana.
 */
public class MensagemNotificacaoBuilder {

    /** Nome do template de resumo cadastrado na Meta. */
    public static final String TEMPLATE_RESUMO = "lembrete_resumo_pendencias";

    /** Nome do template de item individual cadastrado na Meta. */
    public static final String TEMPLATE_ITEM = "lembrete_detalhe_loja";

    /**
     * Monta, na ordem certa (vencidos → vence hoje → vence fim de semana),
     * a lista de pendências que vão gerar uma mensagem de template individual
     * cada. Mantém a mesma prioridade de exibição que a versão antiga em
     * texto único usava, só que agora cada item é uma mensagem separada.
     */
    public List<PendenciaNotificacao> ordenarParaEnvio(
            List<PendenciaNotificacao> vencidos,
            List<PendenciaNotificacao> venceHoje,
            List<PendenciaNotificacao> venceFimDeSemana
    ) {
        var todos = new java.util.ArrayList<PendenciaNotificacao>(
                vencidos.size() + venceHoje.size() + venceFimDeSemana.size());
        todos.addAll(vencidos);
        todos.addAll(venceHoje);
        todos.addAll(venceFimDeSemana);
        return todos;
    }

    /**
     * Parâmetros {{1}}..{{5}} do template {@link #TEMPLATE_RESUMO}:
     * nome do usuário, total de pendências, total de lojas distintas,
     * valor total geral e valor total já vencido.
     * <p>
     * Quando não há nada vencido, {{5}} vem como "R$ 0,00" — o texto fixo
     * do template ("Desse total, {{5}} já está vencido.") continua correto
     * factualmente mesmo nesse caso, então não precisamos de um segundo
     * template condicional só para esse cenário.
     */
    public List<String> parametrosResumo(
            String nomeUsuario,
            List<PendenciaNotificacao> vencidos,
            List<PendenciaNotificacao> venceHoje,
            List<PendenciaNotificacao> venceFimDeSemana
    ) {
        List<PendenciaNotificacao> todos = ordenarParaEnvio(vencidos, venceHoje, venceFimDeSemana);

        long totalPendencias = todos.size();
        long totalLojas = todos.stream().map(PendenciaNotificacao::lojaId).distinct().count();
        BigDecimal valorTotal = todos.stream()
                .map(PendenciaNotificacao::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal valorVencido = vencidos.stream()
                .map(PendenciaNotificacao::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return List.of(
                nomeUsuario,
                String.valueOf(totalPendencias),
                String.valueOf(totalLojas),
                formatarValor(valorTotal),
                formatarValor(valorVencido)
        );
    }

    /**
     * Parâmetros {{1}}..{{4}} do template {@link #TEMPLATE_ITEM} para UMA
     * pendência, na ordem exigida pelo corpo cadastrado na Meta:
     * <pre>
     * 📍 Loja: {{1}}
     * 🔔 Lembrete de pagamento pendente:
     * 📄 Referente a: {{2}}
     * 💰 Valor a pagar: R$ {{3}}
     * 📅 {{4}} Vencimento.
     * </pre>
     * Esse template tem só 4 variáveis (a Meta não aprovou a versão com tipo
     * e fornecedor separados em 5 campos), então {{2}} já vem combinado
     * como "Boleto Light", e {{3}} não inclui o "R$" (o texto fixo do
     * template já tem o "R$" antes da variável). {{4}} é a data de
     * vencimento formatada (dd/MM/yyyy) — o texto fixo já diz "Vencimento."
     * depois da variável, então não dá pra colocar texto livre tipo "venceu
     * há 3 dias" ali sem ficar estranho ("📅 venceu há 3 dias Vencimento.").
     */
    public List<String> parametrosItem(PendenciaNotificacao p) {
        return List.of(
                p.lojaNome(),
                tipoLabel(p.tipo()) + " " + p.fornecedor(),
                formatarValorSemPrefixo(p.valor()),
                formatarData(p.vencimento())
        );
    }

    private String formatarData(LocalDate data) {
        return data.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private String formatarValorSemPrefixo(BigDecimal valor) {
        return String.format(Locale.of("pt", "BR"), "%,.2f", valor);
    }

    private String tipoLabel(PendenciaNotificacao.TipoPendencia tipo) {
        return switch (tipo) {
            case BOLETO -> "Boleto";
            case PIX -> "Pix";
            case CHEQUE -> "Cheque";
        };
    }

    private String formatarValor(BigDecimal valor) {
        return "R$ " + String.format(Locale.of("pt", "BR"), "%,.2f", valor);
    }
}