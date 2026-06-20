package com.agenda.domain.notificacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Monta o texto da mensagem de WhatsApp a partir das pendências já filtradas
 * (vencidas, vencendo hoje, vencendo no fim de semana). Segue a ordem de
 * prioridade combinada: vencidos primeiro, depois hoje, depois fim de semana.
 * Se não houver nada pendente, envia mensagem de confirmação "tudo certo".
 */
public class MensagemNotificacaoBuilder {

    public String construir(
            List<PendenciaNotificacao> vencidos,
            List<PendenciaNotificacao> venceHoje,
            List<PendenciaNotificacao> venceFimDeSemana,
            LocalDate hoje
    ) {
        if (vencidos.isEmpty() && venceHoje.isEmpty() && venceFimDeSemana.isEmpty()) {
            return "✅ *Tudo certo!*\nNenhum boleto, PIX ou cheque pendente hoje.";
        }

        var sb = new StringBuilder();

        if (!vencidos.isEmpty()) {
            sb.append("🔴 *VENCIDOS (").append(vencidos.size()).append(")*\n");
            for (var p : vencidos) {
                long dias = p.diasAtraso(hoje);
                sb.append("• ").append(linhaItem(p))
                        .append(" - venceu há ").append(dias).append(dias == 1 ? " dia" : " dias")
                        .append("\n");
            }
            sb.append("\n");
        }

        if (!venceHoje.isEmpty()) {
            sb.append("🟡 *VENCE HOJE (").append(venceHoje.size()).append(")*\n");
            for (var p : venceHoje) {
                sb.append("• ").append(linhaItem(p)).append("\n");
            }
            sb.append("\n");
        }

        if (!venceFimDeSemana.isEmpty()) {
            sb.append("🔵 *VENCE NO FIM DE SEMANA (").append(venceFimDeSemana.size()).append(")*\n");
            for (var p : venceFimDeSemana) {
                String diaSemana = p.vencimento().getDayOfWeek().getDisplayName(
                        java.time.format.TextStyle.FULL, new Locale("pt", "BR"));
                sb.append("• ").append(linhaItem(p))
                        .append(" - vence ").append(diaSemana)
                        .append(", pode pagar até segunda\n");
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }

    private String linhaItem(PendenciaNotificacao p) {
        return tipoLabel(p.tipo()) + " " + p.fornecedor()
                + " - " + formatarValor(p.valor())
                + " (" + p.lojaNome() + ")";
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