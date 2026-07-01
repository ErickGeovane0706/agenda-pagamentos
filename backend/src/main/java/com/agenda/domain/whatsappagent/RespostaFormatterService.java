package com.agenda.domain.whatsappagent;

import com.agenda.domain.boleto.Boleto;
import com.agenda.domain.cheque.Cheque;
import com.agenda.domain.loja.Loja;
import com.agenda.domain.pix.PagamentoPix;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Monta as mensagens de texto enviadas ao cliente. Deliberadamente NÃO usa
 * LLM aqui — os valores monetários e dados de pagamento vêm direto das
 * entidades buscadas no banco, formatados por código determinístico, para
 * eliminar qualquer risco de um valor ser "reescrito" incorretamente por
 * geração de texto livre.
 */
@Service
public class RespostaFormatterService {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM");

    public String mensagemAjuda() {
        return """
                Oi! Posso te ajudar com informações financeiras da sua empresa. Você pode me perguntar coisas como:

                • "Quanto tenho pra pagar hoje?"
                • "O que vence essa semana na loja Centro?"
                • "Tem boleto atrasado?"
                • "Me manda o código de barras do boleto da Fornecedora X"
                • "Qual a chave PIX do pagamento de hoje?"
                • "Quero ver os dados do cheque número 123"

                Só consigo consultar informações — para pagar, cancelar ou alterar algo, acesse o sistema.""";
    }

    public String mensagemNaoEntendido() {
        return "Não entendi muito bem sua pergunta. Pode reformular? Por exemplo: \"quanto tenho pra pagar hoje\" ou \"o que vence essa semana\".";
    }

    /**
     * Modo compacto: usado quando o volume de itens é grande demais para
     * listar item a item (ver {@code WhatsAppAgentService.LIMITE_ITENS_MODO_DETALHADO}).
     * Mostra só o total por loja, sem entrar em cada boleto/PIX/cheque —
     * o cliente pode pedir "manda detalhado" para ver a lista completa
     * (ver {@code IntencaoAgente.DETALHAR_ULTIMA_CONSULTA}).
     */
    public String formatarResumoPorLoja(List<Boleto> boletos, List<PagamentoPix> pixs, List<Cheque> cheques, String descricaoPeriodo, boolean incluirPagos) {
        List<ItemPendencia> itens = achatarItens(boletos, pixs, cheques);

        if (itens.isEmpty()) {
            return mensagemVazia(descricaoPeriodo);
        }

        BigDecimal totalGeral = somar(itens.stream().map(ItemPendencia::valor));
        List<UUID> ordemLojas = ordenarLojasPorNome(itens);

        String periodo = (descricaoPeriodo != null) ? " " + descricaoPeriodo : "";
        StringBuilder sb = new StringBuilder();
        sb.append("📋 Resumo").append(periodo).append(": ").append(formatarMoeda(totalGeral))
                .append(" em ").append(itens.size()).append(itens.size() == 1 ? " item" : " itens")
                .append(ordemLojas.size() > 1 ? ", " + ordemLojas.size() + " lojas" : "").append("\n");

        if (incluirPagos) {
            BigDecimal totalPago = somar(itens.stream().filter(ItemPendencia::pago).map(ItemPendencia::valor));
            BigDecimal totalAPagar = totalGeral.subtract(totalPago);
            sb.append("✅ Pago: ").append(formatarMoeda(totalPago))
                    .append(" — ⏳ Falta: ").append(formatarMoeda(totalAPagar)).append("\n");
        }

        java.util.Map<UUID, List<ItemPendencia>> porLoja = agruparPorLoja(itens);
        for (UUID lojaId : ordemLojas) {
            List<ItemPendencia> itensDaLoja = porLoja.get(lojaId);
            BigDecimal totalLoja = somar(itensDaLoja.stream().map(ItemPendencia::valor));
            sb.append("\n🏬 ").append(itensDaLoja.get(0).loja().getNome())
                    .append(" — ").append(formatarMoeda(totalLoja))
                    .append(" (").append(itensDaLoja.size()).append(itensDaLoja.size() == 1 ? " item)" : " itens)");
        }

        sb.append("\n\n_Quer ver item por item? Só responder \"detalhar\"._");
        return sb.toString();
    }

    /**
     * Modo completo: lista cada item agrupado por loja (loja é o
     * agrupamento principal — despesa é decidida por loja, não por forma
     * de pagamento). O tipo de pagamento aparece só como indicador curto
     * em cada item. Quando {@code incluirPagos} é verdadeiro, cada item
     * mostra também se já foi pago ou ainda está pendente.
     * <p>
     * {@code totalItensOriginal}: quando não-nulo e maior que a quantidade
     * de itens recebida, indica que a lista já foi truncada (ver
     * {@code WhatsAppAgentService.LIMITE_ITENS_DETALHE_COMPLETO}) — o total
     * exibido passa a ser rotulado como "dos itens exibidos" em vez de
     * "geral", e um aviso é adicionado no fim informando quantos itens
     * ficaram de fora.
     */
    public String formatarListaCompleta(List<Boleto> boletos, List<PagamentoPix> pixs, List<Cheque> cheques, String descricaoPeriodo, boolean incluirPagos, Integer totalItensOriginal) {
        List<ItemPendencia> itens = achatarItens(boletos, pixs, cheques);

        if (itens.isEmpty()) {
            return mensagemVazia(descricaoPeriodo);
        }

        BigDecimal totalExibido = somar(itens.stream().map(ItemPendencia::valor));
        List<UUID> ordemLojas = ordenarLojasPorNome(itens);
        java.util.Map<UUID, List<ItemPendencia>> porLoja = agruparPorLoja(itens);

        boolean truncado = totalItensOriginal != null && totalItensOriginal > itens.size();

        String periodo = (descricaoPeriodo != null) ? " " + descricaoPeriodo : "";
        StringBuilder sb = new StringBuilder();
        sb.append("📋 Pendências").append(periodo).append(":\n");
        if (truncado) {
            sb.append("_Mostrando os ").append(itens.size()).append(" itens com vencimento mais próximo, de ")
                    .append(totalItensOriginal).append(" no total. Peça filtrando por loja para ver o restante._\n");
        }

        for (UUID lojaId : ordemLojas) {
            List<ItemPendencia> itensDaLoja = porLoja.get(lojaId).stream()
                    .sorted(java.util.Comparator.comparing(ItemPendencia::vencimento))
                    .toList();
            BigDecimal totalLoja = somar(itensDaLoja.stream().map(ItemPendencia::valor));

            sb.append("\n🏬 ").append(itensDaLoja.get(0).loja().getNome())
                    .append(" — ").append(formatarMoeda(totalLoja)).append("\n");

            for (ItemPendencia item : itensDaLoja) {
                sb.append("  • ").append(item.emoji()).append(" ").append(item.tipo()).append(": ")
                        .append(item.fornecedor()).append(" — ")
                        .append(formatarMoeda(item.valor())).append(" (venc. ")
                        .append(item.vencimento().format(FORMATO_DATA)).append(")");
                if (incluirPagos) {
                    sb.append(item.pago() ? " — ✅ Pago" : " — ⏳ Pendente");
                }
                sb.append("\n");
            }
        }

        sb.append("\n💰 Total").append(truncado ? " dos itens exibidos" : "").append(": ").append(formatarMoeda(totalExibido));
        return sb.toString();
    }

    private String mensagemVazia(String descricaoPeriodo) {
        String periodo = (descricaoPeriodo != null) ? " " + descricaoPeriodo : "";
        return "Nenhuma pendência encontrada" + periodo + ". Tudo certo! ✅";
    }

    private List<ItemPendencia> achatarItens(List<Boleto> boletos, List<PagamentoPix> pixs, List<Cheque> cheques) {
        List<ItemPendencia> itens = new java.util.ArrayList<>();
        boletos.forEach(b -> itens.add(new ItemPendencia("🧾", "Boleto", b.getFornecedor(), b.getValor(), b.getVencimento(), b.getLoja(), b.getStatus() == com.agenda.domain.boleto.StatusBoleto.PAGO)));
        pixs.forEach(p -> itens.add(new ItemPendencia("💸", "PIX", p.getFornecedor(), p.getValor(), p.getVencimento(), p.getLoja(), p.getStatus() == com.agenda.domain.pix.StatusPix.PAGO)));
        cheques.forEach(c -> itens.add(new ItemPendencia("📝", "Cheque", c.getFornecedor(), c.getValor(), c.getVencimento(), c.getLoja(), c.getStatus() == com.agenda.domain.cheque.StatusCheque.COMPENSADO)));
        return itens;
    }

    private java.util.Map<UUID, List<ItemPendencia>> agruparPorLoja(List<ItemPendencia> itens) {
        return itens.stream()
                .collect(java.util.stream.Collectors.groupingBy(i -> i.loja().getId(), java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()));
    }

    private List<UUID> ordenarLojasPorNome(List<ItemPendencia> itens) {
        java.util.Map<UUID, List<ItemPendencia>> porLoja = agruparPorLoja(itens);
        return porLoja.keySet().stream()
                .sorted((id1, id2) -> porLoja.get(id1).get(0).loja().getNome()
                        .compareToIgnoreCase(porLoja.get(id2).get(0).loja().getNome()))
                .toList();
    }

    /** Item de pendência já "achatado" entre Boleto/PIX/Cheque, pronto para agrupar por loja. */
    private record ItemPendencia(String emoji, String tipo, String fornecedor, BigDecimal valor, java.time.LocalDate vencimento, Loja loja, boolean pago) {}

    public String formatarDadosBoleto(Boleto boleto) {
        StringBuilder sb = new StringBuilder();
        sb.append("🧾 Boleto — ").append(boleto.getFornecedor()).append("\n");
        sb.append("Valor: ").append(formatarMoeda(boleto.getValor())).append("\n");
        sb.append("Vencimento: ").append(boleto.getVencimento().format(FORMATO_DATA)).append("\n");

        if (boleto.getCodigoBarras() != null && !boleto.getCodigoBarras().isBlank()) {
            sb.append("Código de barras:\n").append(boleto.getCodigoBarras());
        } else {
            sb.append("Esse boleto não tem código de barras cadastrado no sistema.");
        }
        return sb.toString();
    }

    public String formatarDadosPix(PagamentoPix pix) {
        StringBuilder sb = new StringBuilder();
        sb.append("💸 PIX — ").append(pix.getFornecedor()).append("\n");
        sb.append("Valor: ").append(formatarMoeda(pix.getValor())).append("\n");
        sb.append("Vencimento: ").append(pix.getVencimento().format(FORMATO_DATA)).append("\n");
        sb.append("Chave PIX (").append(pix.getTipoChave() != null ? pix.getTipoChave() : "tipo não informado").append("):\n");
        sb.append(pix.getChavePix());
        return sb.toString();
    }

    public String formatarDadosCheque(Cheque cheque) {
        StringBuilder sb = new StringBuilder();
        sb.append("📝 Cheque — ").append(cheque.getFornecedor()).append("\n");
        sb.append("Valor: ").append(formatarMoeda(cheque.getValor())).append("\n");
        sb.append("Vencimento: ").append(cheque.getVencimento().format(FORMATO_DATA)).append("\n");
        sb.append("Status: ").append(traduzirStatusCheque(cheque)).append("\n");
        if (cheque.getNumeroCheque() != null && !cheque.getNumeroCheque().isBlank()) {
            sb.append("Número: ").append(cheque.getNumeroCheque()).append("\n");
        }
        if (cheque.getBanco() != null) {
            sb.append("Banco: ").append(cheque.getBanco().getNome());
        }
        return sb.toString();
    }

    public String formatarListaParaDesambiguar(List<Boleto> boletos, List<PagamentoPix> pixs) {
        StringBuilder sb = new StringBuilder("Encontrei mais de um item, qual deles você quer?\n\n");
        int i = 1;
        for (Boleto b : boletos) {
            sb.append(i++).append(". [Boleto] ").append(b.getFornecedor()).append(" — ")
                    .append(formatarMoeda(b.getValor())).append(" (venc. ").append(b.getVencimento().format(FORMATO_DATA)).append(")\n");
        }
        for (PagamentoPix p : pixs) {
            sb.append(i++).append(". [PIX] ").append(p.getFornecedor()).append(" — ")
                    .append(formatarMoeda(p.getValor())).append(" (venc. ").append(p.getVencimento().format(FORMATO_DATA)).append(")\n");
        }
        sb.append("\nResponda com o nome do fornecedor para eu te mandar os dados certos.");
        return sb.toString();
    }

    public String formatarListaChequesParaDesambiguar(List<Cheque> cheques) {
        StringBuilder sb = new StringBuilder("Encontrei mais de um cheque, qual deles você quer?\n\n");
        int i = 1;
        for (Cheque c : cheques) {
            sb.append(i++).append(". ").append(c.getFornecedor());
            if (c.getNumeroCheque() != null && !c.getNumeroCheque().isBlank()) {
                sb.append(" (nº ").append(c.getNumeroCheque()).append(")");
            }
            sb.append(" — ").append(formatarMoeda(c.getValor()))
                    .append(" (venc. ").append(c.getVencimento().format(FORMATO_DATA)).append(")\n");
        }
        sb.append("\nResponda com o nome do fornecedor ou número do cheque.");
        return sb.toString();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private BigDecimal somar(Stream<BigDecimal> valores) {
        return valores.reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String formatarMoeda(BigDecimal valor) {
        return "R$ " + valor.setScale(2, java.math.RoundingMode.HALF_UP).toString().replace(".", ",");
    }

    private String traduzirStatusCheque(Cheque cheque) {
        return switch (cheque.getStatus()) {
            case PENDENTE -> "Pendente";
            case COMPENSADO -> "Compensado";
            case DEVOLVIDO -> "Devolvido (sem fundos)";
            case CANCELADO -> "Cancelado";
        };
    }
}