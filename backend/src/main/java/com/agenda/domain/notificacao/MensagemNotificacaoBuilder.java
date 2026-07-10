package com.agenda.domain.notificacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

    /**
     * Monta o texto livre detalhado (item a item, agrupado por loja) das
     * pendências do lembrete. Diferente de {@link #parametrosResumo} e
     * {@link #parametrosItem}, este NÃO é template: só é enviado quando o
     * cliente responde "detalhar" ao lembrete, já dentro da janela de 24h,
     * então pode ter quebras de linha à vontade.
     * <p>
     * O layout espelha o do agente conversacional
     * ({@code RespostaFormatterService#formatarListaCompleta}) para o cliente
     * ver o mesmo formato venha o detalhe do chat ou do lembrete automático.
     */
    public String textoDetalhado(
            List<PendenciaNotificacao> vencidos,
            List<PendenciaNotificacao> venceHoje,
            List<PendenciaNotificacao> venceFimDeSemana
    ) {
        List<PendenciaNotificacao> todos = ordenarParaEnvio(vencidos, venceHoje, venceFimDeSemana);

        Map<UUID, List<PendenciaNotificacao>> porLoja = todos.stream()
                .collect(Collectors.groupingBy(PendenciaNotificacao::lojaId, LinkedHashMap::new, Collectors.toList()));

        List<UUID> ordemLojas = porLoja.keySet().stream()
                .sorted(Comparator.comparing(id -> porLoja.get(id).get(0).lojaNome(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        StringBuilder sb = new StringBuilder("📋 Pendências:\n");
        for (UUID lojaId : ordemLojas) {
            List<PendenciaNotificacao> itensDaLoja = porLoja.get(lojaId).stream()
                    .sorted(Comparator.comparing(PendenciaNotificacao::vencimento))
                    .toList();
            BigDecimal totalLoja = itensDaLoja.stream()
                    .map(PendenciaNotificacao::valor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            sb.append("\n🏬 ").append(itensDaLoja.get(0).lojaNome())
                    .append(" — ").append(formatarValor(totalLoja)).append("\n");

            for (PendenciaNotificacao p : itensDaLoja) {
                sb.append("  • ").append(emoji(p.tipo())).append(" ").append(tipoLabel(p.tipo())).append(": ")
                        .append(p.fornecedor()).append(" — ").append(formatarValor(p.valor()))
                        .append(" (venc. ").append(formatarDiaMes(p.vencimento())).append(")\n");
            }
        }

        BigDecimal totalGeral = todos.stream()
                .map(PendenciaNotificacao::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        sb.append("\n💰 Total: ").append(formatarValor(totalGeral));
        return sb.toString();
    }

    private String emoji(PendenciaNotificacao.TipoPendencia tipo) {
        return switch (tipo) {
            case BOLETO -> "🧾";
            case PIX -> "💸";
            case CHEQUE -> "📝";
        };
    }

    private String formatarDiaMes(LocalDate data) {
        return data.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"));
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