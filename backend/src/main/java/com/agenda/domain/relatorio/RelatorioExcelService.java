package com.agenda.domain.relatorio;

import com.agenda.domain.boleto.BoletoRepository;
import com.agenda.domain.cheque.ChequeRepository;
import com.agenda.domain.pix.PagamentoPixRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Geração de relatório em Excel (.xlsx) com Apache POI.
 * Inclui cabeçalho com período, cards de resumo geral com
 * cores, seções por tipo de pagamento com linhas alternadas,
 * cores por status e subtotais ao final de cada seção.
 * Cores em XSSFColor (hex) para visual profissional.
 */
@Service
@RequiredArgsConstructor
public class RelatorioExcelService {

    private final BoletoRepository boletoRepository;
    private final PagamentoPixRepository pagamentoPixRepository;
    private final ChequeRepository chequeRepository;

    private static final XSSFColor COR_VERDE      = cor("1D9E75");
    private static final XSSFColor COR_AZUL        = cor("185FA5");
    private static final XSSFColor COR_LARANJA     = cor("BA7517");
    private static final XSSFColor COR_CINZA_DARK  = cor("1E293B");
    private static final XSSFColor COR_CINZA_LIGHT = cor("F1F5F9");
    private static final XSSFColor COR_BRANCO      = cor("FFFFFF");
    private static final XSSFColor COR_BORDA       = cor("E2E8F0");

    public byte[] gerarExcel(UUID empresaId, UUID lojaId, LocalDate de, LocalDate ate) throws IOException {
        var boletos = boletoRepository.listarParaCsv(empresaId, lojaId, de, ate);
        var pixList = pagamentoPixRepository.listarParaCsv(empresaId, lojaId, de, ate);
        var cheques = chequeRepository.listarParaCsv(empresaId, lojaId, de, ate);

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("Relatório");
            sheet.setDisplayGridlines(false);
            sheet.setDefaultColumnWidth(18);
            sheet.setColumnWidth(0, 8000);
            sheet.setColumnWidth(1, 5000);
            sheet.setColumnWidth(2, 4000);
            sheet.setColumnWidth(3, 4000);

            int linha = 0;
            linha = criarCabecalhoRelatorio(wb, sheet, de, ate, lojaId, linha);
            linha = criarResumoGeral(wb, sheet, boletos, pixList, cheques, linha);

            if (!boletos.isEmpty())
                linha = criarSecao(wb, sheet, "BOLETOS", COR_AZUL, boletos, linha);

            if (!pixList.isEmpty())
                linha = criarSecao(wb, sheet, "PIX", COR_VERDE, pixList, linha);

            if (!cheques.isEmpty())
                linha = criarSecao(wb, sheet, "CHEQUES", COR_LARANJA, cheques, linha);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private int criarCabecalhoRelatorio(XSSFWorkbook wb, XSSFSheet sheet,
                                         LocalDate de, LocalDate ate, UUID lojaId, int linha) {
        Row rowTitulo = sheet.createRow(linha++);
        rowTitulo.setHeightInPoints(36);
        Cell titulo = rowTitulo.createCell(0);
        titulo.setCellValue("Relatório de Pagamentos");
        titulo.setCellStyle(estilo(wb, s -> {
            s.setFont(fonte(wb, 18, true, COR_CINZA_DARK));
            s.setFillForegroundColor(COR_BRANCO);
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
        }));
        sheet.addMergedRegion(new CellRangeAddress(linha - 1, linha - 1, 0, 3));

        Row rowPeriodo = sheet.createRow(linha++);
        rowPeriodo.setHeightInPoints(20);
        Cell periodo = rowPeriodo.createCell(0);
        periodo.setCellValue("Período: " + fmtData(de) + " a " + fmtData(ate)
            + "   |   Loja: " + (lojaId != null ? "" : "Todas as lojas")
            + "   |   Gerado em: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")));
        periodo.setCellStyle(estilo(wb, s -> {
            s.setFont(fonte(wb, 10, false, cor("64748B")));
        }));
        sheet.addMergedRegion(new CellRangeAddress(linha - 1, linha - 1, 0, 3));
        linha++;
        return linha;
    }

    private int criarResumoGeral(XSSFWorkbook wb, XSSFSheet sheet,
                                  List<Object[]> boletos, List<Object[]> pixList,
                                  List<Object[]> cheques, int linha) {

        var totalGeral = calcularTotal(boletos, null).add(calcularTotal(pixList, null)).add(calcularTotal(cheques, null));
        var totalPago  = calcularTotal(boletos, "PAGO").add(calcularTotal(pixList, "PAGO"));
        var totalComp  = calcularTotal(cheques, "COMPENSADO");
        var totalPend  = calcularTotal(boletos, "PENDENTE").add(calcularTotal(pixList, "PENDENTE")).add(calcularTotal(cheques, "PENDENTE"));

        Row rowTitulo = sheet.createRow(linha++);
        rowTitulo.setHeightInPoints(24);
        Cell t = rowTitulo.createCell(0);
        t.setCellValue("RESUMO GERAL");
        t.setCellStyle(estilo(wb, s -> {
            s.setFont(fonte(wb, 11, true, COR_BRANCO));
            s.setFillForegroundColor(COR_CINZA_DARK);
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
        }));
        sheet.addMergedRegion(new CellRangeAddress(linha - 1, linha - 1, 0, 3));

        String[][] cards = {
            {"Total Geral", fmtBRL(totalGeral), "1E293B"},
            {"Total Pago",  fmtBRL(totalPago),  "1D9E75"},
            {"Compensado",  fmtBRL(totalComp),  "185FA5"},
            {"Pendente",    fmtBRL(totalPend),  "D85A30"},
        };

        Row rowLabel = sheet.createRow(linha++);
        Row rowValor = sheet.createRow(linha++);
        rowLabel.setHeightInPoints(16);
        rowValor.setHeightInPoints(26);

        for (int i = 0; i < cards.length; i++) {
            var corCard = cor(cards[i][2]);
            Cell label = rowLabel.createCell(i);
            label.setCellValue(cards[i][0]);
            label.setCellStyle(estilo(wb, s -> {
                s.setFont(fonte(wb, 9, false, cor("64748B")));
                s.setFillForegroundColor(COR_CINZA_LIGHT);
                s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                s.setAlignment(HorizontalAlignment.CENTER);
                bordas(s);
            }));
            Cell valor = rowValor.createCell(i);
            valor.setCellValue(cards[i][1]);
            var corFinal = corCard;
            valor.setCellStyle(estilo(wb, s -> {
                s.setFont(fonte(wb, 13, true, corFinal));
                s.setFillForegroundColor(COR_CINZA_LIGHT);
                s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                s.setAlignment(HorizontalAlignment.CENTER);
                s.setVerticalAlignment(VerticalAlignment.CENTER);
                bordas(s);
            }));
        }
        linha++;
        return linha;
    }

    private int criarSecao(XSSFWorkbook wb, XSSFSheet sheet, String titulo,
                            XSSFColor cor, List<Object[]> dados, int linha) {
        Row rowTitulo = sheet.createRow(linha++);
        rowTitulo.setHeightInPoints(22);
        Cell t = rowTitulo.createCell(0);
        t.setCellValue(titulo);
        t.setCellStyle(estilo(wb, s -> {
            s.setFont(fonte(wb, 11, true, COR_BRANCO));
            s.setFillForegroundColor(cor);
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
        }));
        sheet.addMergedRegion(new CellRangeAddress(linha - 1, linha - 1, 0, 3));

        Row rowHeader = sheet.createRow(linha++);
        rowHeader.setHeightInPoints(18);
        String[] headers = {"Fornecedor", "Valor", "Vencimento", "Status"};
        for (int i = 0; i < headers.length; i++) {
            Cell c = rowHeader.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(estilo(wb, s -> {
                s.setFont(fonte(wb, 9, true, COR_CINZA_DARK));
                s.setFillForegroundColor(COR_CINZA_LIGHT);
                s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                bordas(s);
            }));
        }

        boolean par = false;
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Object[] row : dados) {
            var fornecedor = str(row[0]);
            var valor = (BigDecimal) row[1];
            var data = row[2] instanceof LocalDate d ? d : LocalDate.parse(row[2].toString());
            var status = row[3].toString();

            Row dataRow = sheet.createRow(linha++);
            dataRow.setHeightInPoints(16);
            boolean isPar = par;

            String[] cols = {fornecedor, fmtBRL(valor), fmtData(data), tradStatus(status)};
            for (int i = 0; i < cols.length; i++) {
                Cell c = dataRow.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(estilo(wb, s -> {
                    s.setFont(fonte(wb, 10, false, COR_CINZA_DARK));
                    s.setFillForegroundColor(isPar ? COR_BRANCO : COR_CINZA_LIGHT);
                    s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                    bordas(s);
                }));
            }

            Cell cellStatus = dataRow.getCell(3);
            var corStatus = switch (status.toUpperCase()) {
                case "PAGO" -> COR_VERDE;
                case "COMPENSADO" -> COR_AZUL;
                case "PENDENTE" -> cor("D85A30");
                case "CANCELADO" -> cor("94A3B8");
                case "VENCIDO" -> cor("DC2626");
                default -> COR_CINZA_DARK;
            };
            cellStatus.getCellStyle().setFont(fonte(wb, 10, true, corStatus));

            subtotal = subtotal.add(valor != null ? valor : BigDecimal.ZERO);
            par = !par;
        }

        Row rowSub = sheet.createRow(linha++);
        rowSub.setHeightInPoints(18);
        Cell subLabel = rowSub.createCell(0);
        subLabel.setCellValue("Subtotal " + titulo);
        subLabel.setCellStyle(estilo(wb, s -> {
            s.setFont(fonte(wb, 10, true, COR_CINZA_DARK));
            s.setFillForegroundColor(COR_CINZA_LIGHT);
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            bordas(s);
        }));
        Cell subValor = rowSub.createCell(1);
        subValor.setCellValue(fmtBRL(subtotal));
        subValor.setCellStyle(estilo(wb, s -> {
            s.setFont(fonte(wb, 10, true, COR_CINZA_DARK));
            s.setFillForegroundColor(COR_CINZA_LIGHT);
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            bordas(s);
        }));

        linha++;
        return linha;
    }

    private BigDecimal calcularTotal(List<Object[]> dados, String status) {
        BigDecimal total = BigDecimal.ZERO;
        for (Object[] row : dados) {
            if (status != null && !row[3].toString().equalsIgnoreCase(status)) continue;
            var valor = (BigDecimal) row[1];
            total = total.add(valor != null ? valor : BigDecimal.ZERO);
        }
        return total;
    }

    private static XSSFColor cor(String hex) {
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return new XSSFColor(new byte[]{(byte) r, (byte) g, (byte) b}, null);
    }

    private XSSFFont fonte(XSSFWorkbook wb, int size, boolean bold, XSSFColor cor) {
        XSSFFont f = wb.createFont();
        f.setFontName("Aptos Narrow");
        f.setFontHeightInPoints((short) size);
        f.setBold(bold);
        f.setColor(cor);
        return f;
    }

    private XSSFCellStyle estilo(XSSFWorkbook wb, Consumer<XSSFCellStyle> config) {
        XSSFCellStyle s = wb.createCellStyle();
        config.accept(s);
        return s;
    }

    private void bordas(XSSFCellStyle s) {
        s.setBorderBottom(BorderStyle.THIN);
        s.setBottomBorderColor(COR_BORDA);
    }

    private String fmtBRL(BigDecimal v) {
        return "R$ " + String.format("%.2f", v).replace('.', ',');
    }

    private String fmtData(LocalDate d) {
        return d != null ? d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "";
    }

    private String str(Object o) {
        return o != null ? o.toString() : "";
    }

    private String tradStatus(String s) {
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
