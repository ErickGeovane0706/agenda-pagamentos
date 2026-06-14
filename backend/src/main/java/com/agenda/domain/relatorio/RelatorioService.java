package com.agenda.domain.relatorio;

import com.agenda.domain.boleto.BoletoRepository;
import com.agenda.domain.cheque.ChequeRepository;
import com.agenda.domain.pix.PagamentoPixRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RelatorioService {

    private final BoletoRepository boletoRepository;
    private final PagamentoPixRepository pagamentoPixRepository;
    private final ChequeRepository chequeRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> gerar(UUID empresaId, LocalDate de, LocalDate ate, UUID lojaId, String tipo, String status) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("periodo", Map.of("de", de.toString(), "ate", ate.toString()));

        boolean todos = tipo == null || tipo.equalsIgnoreCase("TODOS");
        var resumoBoletos = todos || tipo.equalsIgnoreCase("BOLETO")
            ? montarResumoBoletos(empresaId, lojaId, de, ate, status)
            : zerado("boletos");
        var resumoPix = todos || tipo.equalsIgnoreCase("PIX")
            ? montarResumoPix(empresaId, lojaId, de, ate, status)
            : zerado("pix");
        var resumoCheques = todos || tipo.equalsIgnoreCase("CHEQUE")
            ? montarResumoCheques(empresaId, lojaId, de, ate, status)
            : zerado("cheques");

        var totalGeral = ((BigDecimal) resumoBoletos.get("valor"))
            .add((BigDecimal) resumoPix.get("valor"))
            .add((BigDecimal) resumoCheques.get("valor"));

        resultado.put("totalGeral", totalGeral);
        resultado.put("boletos", resumoBoletos);
        resultado.put("pix", resumoPix);
        resultado.put("cheques", resumoCheques);
        return resultado;
    }

    @SuppressWarnings("unchecked")
    public byte[] gerarCsv(UUID empresaId, LocalDate de, LocalDate ate, UUID lojaId, String tipo, String status) {
        var relatorio = gerar(empresaId, de, ate, lojaId, tipo, status);
        var sb = new StringBuilder("\uFEFF");
        var dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        sb.append("RELAT\u00d3RIO DE PAGAMENTOS\n");
        sb.append("Per\u00edodo:;").append(de.format(dtf)).append(" a ").append(ate.format(dtf)).append("\n");
        sb.append("Loja:;").append(lojaId != null ? "" : "Todas as lojas").append("\n");
        sb.append("Gerado em:;").append(LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("dd/MM/yyyy '\u00e0s' HH:mm"))).append("\n\n");

        var totalPago = BigDecimal.ZERO;
        var totalCompensado = BigDecimal.ZERO;
        var totalPendente = BigDecimal.ZERO;

        for (var key : List.of("boletos", "pix", "cheques")) {
            var dados = (Map<String, Object>) relatorio.get(key);
            if (dados == null) continue;
            totalPago = totalPago.add(getBD(dados, "valorPago"));
            totalCompensado = totalCompensado.add(getBD(dados, "valorCompensado"));
            totalPendente = totalPendente.add(getBD(dados, "valorPendente"));
        }

        var totalGeral = totalPago.add(totalCompensado).add(totalPendente);

        sb.append("RESUMO GERAL\n");
        sb.append("Total Geral;").append(fmt(totalGeral)).append("\n");
        sb.append("Total Pago;").append(fmt(totalPago)).append("\n");
        sb.append("Total Compensado;").append(fmt(totalCompensado)).append("\n");
        sb.append("Total Pendente;").append(fmt(totalPendente)).append("\n\n");

        escreverSecao(sb, "BOLETOS", "Fornecedor;Valor;Vencimento;Status",
            boletoRepository.listarParaCsv(empresaId, lojaId, de, ate),
            row -> new String[]{str(row[0]), fmt((BigDecimal) row[1]), dtf.format((LocalDate) row[2]), tradStatus(str(row[3]))});

        escreverSecao(sb, "PIX", "Fornecedor;Valor;Vencimento;Status",
            pagamentoPixRepository.listarParaCsv(empresaId, lojaId, de, ate),
            row -> new String[]{str(row[0]), fmt((BigDecimal) row[1]), dtf.format((LocalDate) row[2]), tradStatus(str(row[3]))});

        escreverSecao(sb, "CHEQUES", "Fornecedor;Valor;Vencimento;Status",
            chequeRepository.listarParaCsv(empresaId, lojaId, de, ate),
            row -> new String[]{str(row[0]), fmt((BigDecimal) row[1]), dtf.format((LocalDate) row[2]), tradStatus(str(row[3]))});

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void escreverSecao(StringBuilder sb, String titulo, String cabecalho,
                                List<Object[]> rows,
                                java.util.function.Function<Object[], String[]> extrator) {
        sb.append(titulo).append("\n");
        sb.append(cabecalho).append("\n");
        BigDecimal subtotal = BigDecimal.ZERO;
        for (var row : rows) {
            var cols = extrator.apply(row);
            sb.append(esc(cols[0])).append(";").append(cols[1]).append(";")
              .append(cols[2]).append(";").append(cols[3]).append("\n");
            subtotal = subtotal.add((BigDecimal) row[1]);
        }
        sb.append("Subtotal ").append(titulo).append(";").append(fmt(subtotal)).append("\n\n");
    }

    private String fmt(BigDecimal v) {
        return "R$ " + String.format("%.2f", v).replace('.', ',');
    }

    private String str(Object o) {
        return o != null ? o.toString() : "";
    }

    private String esc(String v) {
        if (v == null || v.isEmpty()) return v;
        if (v.contains(";") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private BigDecimal getBD(Map<String, Object> m, String k) {
        var v = m.get(k);
        return v instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
    }

    private List<Object[]> filtrarPorStatus(List<Object[]> rows, String status, Set<String> statusPagos, Set<String> statusPendentes) {
        if (status == null || status.equalsIgnoreCase("TODOS") || status.equalsIgnoreCase("TOTAL")) {
            return rows;
        }
        if (status.equalsIgnoreCase("PAGO")) {
            return rows.stream()
                .filter(row -> statusPagos.contains(row[0].toString()))
                .collect(Collectors.toList());
        }
        if (status.equalsIgnoreCase("PENDENTE")) {
            return rows.stream()
                .filter(row -> statusPendentes.contains(row[0].toString()))
                .collect(Collectors.toList());
        }
        return rows;
    }

    private Map<String, Object> zerado(String tipo) {
        return switch (tipo) {
            case "cheques" -> Map.of(
                "total", 0L, "valor", BigDecimal.ZERO,
                "compensado", 0L, "pendente", 0L, "devolvido", 0L, "cancelado", 0L,
                "valorCompensado", BigDecimal.ZERO, "valorPendente", BigDecimal.ZERO,
                "valorDevolvido", BigDecimal.ZERO, "valorCancelado", BigDecimal.ZERO);
            default -> Map.of(
                "total", 0L, "valor", BigDecimal.ZERO,
                "pago", 0L, "pendente", 0L, "vencido", 0L, "cancelado", 0L,
                "valorPago", BigDecimal.ZERO, "valorPendente", BigDecimal.ZERO,
                "valorVencido", BigDecimal.ZERO, "valorCancelado", BigDecimal.ZERO);
        };
    }

    private Map<String, Object> montarResumoBoletos(UUID empresaId, UUID lojaId, LocalDate de, LocalDate ate, String status) {
        var rows = filtrarPorStatus(
            boletoRepository.resumo(empresaId, lojaId, de, ate),
            status, Set.of("PAGO"), Set.of("PENDENTE", "VENCIDO"));
        long total = 0, pago = 0, pendente = 0, vencido = 0, cancelado = 0;
        BigDecimal valorTotal = BigDecimal.ZERO;
        BigDecimal valorPago = BigDecimal.ZERO, valorPendente = BigDecimal.ZERO;
        BigDecimal valorVencido = BigDecimal.ZERO, valorCancelado = BigDecimal.ZERO;
        for (var row : rows) {
            String s = row[0].toString();
            long count = (long) row[1];
            var valor = (BigDecimal) row[2];
            total += count;
            if (valor != null) valorTotal = valorTotal.add(valor);
            switch (s) {
                case "PAGO" -> { pago = count; valorPago = valor != null ? valor : BigDecimal.ZERO; }
                case "PENDENTE" -> { pendente = count; valorPendente = valor != null ? valor : BigDecimal.ZERO; }
                case "VENCIDO" -> { vencido = count; valorVencido = valor != null ? valor : BigDecimal.ZERO; }
                case "CANCELADO" -> { cancelado = count; valorCancelado = valor != null ? valor : BigDecimal.ZERO; }
            }
        }
        return Map.of(
            "total", total, "valor", valorTotal,
            "pago", pago, "pendente", pendente, "vencido", vencido, "cancelado", cancelado,
            "valorPago", valorPago, "valorPendente", valorPendente,
            "valorVencido", valorVencido, "valorCancelado", valorCancelado);
    }

    private Map<String, Object> montarResumoPix(UUID empresaId, UUID lojaId, LocalDate de, LocalDate ate, String status) {
        var rows = filtrarPorStatus(
            pagamentoPixRepository.resumo(empresaId, lojaId, de, ate),
            status, Set.of("PAGO"), Set.of("PENDENTE", "VENCIDO"));
        long total = 0, pago = 0, pendente = 0, vencido = 0, cancelado = 0;
        BigDecimal valorTotal = BigDecimal.ZERO;
        BigDecimal valorPago = BigDecimal.ZERO, valorPendente = BigDecimal.ZERO;
        BigDecimal valorVencido = BigDecimal.ZERO, valorCancelado = BigDecimal.ZERO;
        for (var row : rows) {
            String s = row[0].toString();
            long count = (long) row[1];
            var valor = (BigDecimal) row[2];
            total += count;
            if (valor != null) valorTotal = valorTotal.add(valor);
            switch (s) {
                case "PAGO" -> { pago = count; valorPago = valor != null ? valor : BigDecimal.ZERO; }
                case "PENDENTE" -> { pendente = count; valorPendente = valor != null ? valor : BigDecimal.ZERO; }
                case "VENCIDO" -> { vencido = count; valorVencido = valor != null ? valor : BigDecimal.ZERO; }
                case "CANCELADO" -> { cancelado = count; valorCancelado = valor != null ? valor : BigDecimal.ZERO; }
            }
        }
        return Map.of(
            "total", total, "valor", valorTotal,
            "pago", pago, "pendente", pendente, "vencido", vencido, "cancelado", cancelado,
            "valorPago", valorPago, "valorPendente", valorPendente,
            "valorVencido", valorVencido, "valorCancelado", valorCancelado);
    }

    private Map<String, Object> montarResumoCheques(UUID empresaId, UUID lojaId, LocalDate de, LocalDate ate, String status) {
        var rows = filtrarPorStatus(
            chequeRepository.resumo(empresaId, lojaId, de, ate),
            status, Set.of("COMPENSADO"), Set.of("PENDENTE", "DEVOLVIDO"));
        long total = 0, compensado = 0, pendente = 0, devolvido = 0, cancelado = 0;
        BigDecimal valorTotal = BigDecimal.ZERO;
        BigDecimal valorCompensado = BigDecimal.ZERO, valorPendente = BigDecimal.ZERO;
        BigDecimal valorDevolvido = BigDecimal.ZERO, valorCancelado = BigDecimal.ZERO;
        for (var row : rows) {
            String s = row[0].toString();
            long count = (long) row[1];
            var valor = (BigDecimal) row[2];
            total += count;
            if (valor != null) valorTotal = valorTotal.add(valor);
            switch (s) {
                case "COMPENSADO" -> { compensado = count; valorCompensado = valor != null ? valor : BigDecimal.ZERO; }
                case "PENDENTE" -> { pendente = count; valorPendente = valor != null ? valor : BigDecimal.ZERO; }
                case "DEVOLVIDO" -> { devolvido = count; valorDevolvido = valor != null ? valor : BigDecimal.ZERO; }
                case "CANCELADO" -> { cancelado = count; valorCancelado = valor != null ? valor : BigDecimal.ZERO; }
            }
        }
        return Map.of(
            "total", total, "valor", valorTotal,
            "compensado", compensado, "pendente", pendente,
            "devolvido", devolvido, "cancelado", cancelado,
            "valorCompensado", valorCompensado, "valorPendente", valorPendente,
            "valorDevolvido", valorDevolvido, "valorCancelado", valorCancelado);
    }

    private static String tradStatus(String s) {
        if (s == null) return "";
        return switch (s.toUpperCase()) {
            case "PAGO" -> "Pago";
            case "PENDENTE" -> "Pendente";
            case "COMPENSADO" -> "Compensado";
            case "CANCELADO" -> "Cancelado";
            case "VENCIDO" -> "Vencido";
            case "DEVOLVIDO" -> "Devolvido";
            default -> s;
        };
    }
}
