package com.agenda.domain.whatsappagent;

import com.agenda.domain.boleto.Boleto;
import com.agenda.domain.cheque.Cheque;
import com.agenda.domain.pix.PagamentoPix;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
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

    public String formatarResumoPendencias(List<Boleto> boletos, List<PagamentoPix> pixs, List<Cheque> cheques, String descricaoPeriodo) {
        BigDecimal totalBoletos = somar(boletos.stream().map(Boleto::getValor));
        BigDecimal totalPix = somar(pixs.stream().map(PagamentoPix::getValor));
        BigDecimal totalCheques = somar(cheques.stream().map(Cheque::getValor));
        BigDecimal totalGeral = totalBoletos.add(totalPix).add(totalCheques);

        int totalItens = boletos.size() + pixs.size() + cheques.size();

        if (totalItens == 0) {
            String periodo = (descricaoPeriodo != null) ? " " + descricaoPeriodo : "";
            return "Nenhuma pendência encontrada" + periodo + ". Tudo certo! ✅";
        }

        StringBuilder sb = new StringBuilder();
        String periodo = (descricaoPeriodo != null) ? " " + descricaoPeriodo : "";
        sb.append("📋 Pendências").append(periodo).append(":\n\n");

        if (!boletos.isEmpty()) {
            sb.append("🧾 Boletos (").append(boletos.size()).append("): ").append(formatarMoeda(totalBoletos)).append("\n");
            for (Boleto b : ordenarBoletosPorVencimento(boletos)) {
                sb.append("  • ").append(b.getFornecedor()).append(" — ")
                        .append(formatarMoeda(b.getValor())).append(" (venc. ")
                        .append(b.getVencimento().format(FORMATO_DATA)).append(")\n");
            }
        }
        if (!pixs.isEmpty()) {
            sb.append("💸 PIX (").append(pixs.size()).append("): ").append(formatarMoeda(totalPix)).append("\n");
            for (PagamentoPix p : pixs) {
                sb.append("  • ").append(p.getFornecedor()).append(" — ")
                        .append(formatarMoeda(p.getValor())).append(" (venc. ")
                        .append(p.getVencimento().format(FORMATO_DATA)).append(")\n");
            }
        }
        if (!cheques.isEmpty()) {
            sb.append("📝 Cheques (").append(cheques.size()).append("): ").append(formatarMoeda(totalCheques)).append("\n");
            for (Cheque c : cheques) {
                sb.append("  • ").append(c.getFornecedor()).append(" — ")
                        .append(formatarMoeda(c.getValor())).append(" (venc. ")
                        .append(c.getVencimento().format(FORMATO_DATA)).append(")\n");
            }
        }

        sb.append("\n💰 Total: ").append(formatarMoeda(totalGeral));
        return sb.toString();
    }

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

    private List<Boleto> ordenarBoletosPorVencimento(List<Boleto> boletos) {
        return boletos.stream()
                .sorted((a, b) -> a.getVencimento().compareTo(b.getVencimento()))
                .toList();
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
