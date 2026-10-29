package com.agenda.domain.relatorio;

import com.agenda.domain.boleto.BoletoRepository;
import com.agenda.domain.cheque.ChequeRepository;
import com.agenda.domain.loja.LojaRepository;
import com.agenda.domain.pix.PagamentoPixRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.XDDFColor;
import org.apache.poi.xddf.usermodel.XDDFLineProperties;
import org.apache.poi.xddf.usermodel.XDDFNoFillProperties;
import org.apache.poi.xddf.usermodel.XDDFShapeProperties;
import org.apache.poi.xddf.usermodel.XDDFSolidFillProperties;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTDLbls;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTDPt;
import org.openxmlformats.schemas.drawingml.x2006.main.CTShapeProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Excel do relatório de pagamentos, no modelo aprovado com o cliente
 * (MODELO A4): uma tabela só, com a coluna Loja — o cliente não sabia de qual
 * loja era cada boleto —, valores e datas de verdade, e três gráficos à
 * direita.
 * <p>
 * Os cartões do topo e os gráficos são fórmulas que somam <b>só as linhas
 * visíveis</b> ({@code SUBTOTAL(109, OFFSET(...))}): filtrar a tabela no Excel
 * filtra tudo junto. Os filtros da tela (loja, tipo, situação) decidem o que
 * entra no arquivo. Cancelado não entra em nenhum total — fica num bloco à
 * parte, como na tela.
 * <p>
 * <b>Antes de mexer, leia {@code docs/adr/0011-excel-do-relatorio.md}.</b> Âncora
 * do gráfico, overlay, valor em cache, linhas visíveis: cada detalhe já quebrou
 * uma vez sem dar erro nenhum — o arquivo abre, só que errado.
 */
@Service
@RequiredArgsConstructor
public class RelatorioExcelService {

    static final String ABA = "Lançamentos";
    static final String ABA_DADOS = "Dados dos gráficos";

    private static final String PAGO = "Pago";
    private static final String PENDENTE = "Pendente";
    private static final String VENCIDO = "Vencido";
    private static final String CANCELADO = "Cancelado";
    private static final List<String> SITUACOES = List.of(PAGO, PENDENTE, VENCIDO);

    /** Linha 10 do Excel (base 0): as nove de cima ficam fixas. */
    private static final int INI = 9;
    /** A faixa do cabeçalho vai até a coluna O, cobrindo a área dos gráficos. */
    private static final int ULTIMA_COL_FAIXA = 14;
    private static final int COL_GRAFICO = 7;
    private static final String BRL = "\"R$ \"#,##0.00";
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    private static final String NAVY = "14213D";
    private static final String TEXTO = "1F2937";
    /** forte (barra/traço), fundo do cartão, texto. */
    private static final Map<String, String[]> COR = Map.of(
        PAGO, new String[]{"2FBF71", "E8F8EF", "16844A"},
        PENDENTE, new String[]{"F5A04A", "FFF4E7", "B86A12"},
        VENCIDO, new String[]{"EC5D5D", "FDEEEE", "C23A3A"});

    private final BoletoRepository boletoRepository;
    private final PagamentoPixRepository pagamentoPixRepository;
    private final ChequeRepository chequeRepository;
    private final LojaRepository lojaRepository;

    record Lancamento(LocalDate vencimento, String loja, String tipo, String fornecedor, String situacao, BigDecimal valor) {}

    @Transactional(readOnly = true)
    public byte[] gerarExcel(UUID empresaId, UUID lojaId, LocalDate de, LocalDate ate, String tipo, String status) throws IOException {
        var todos = carregar(empresaId, lojaId, de, ate, tipo);
        var ativos = todos.stream()
            .filter(l -> !l.situacao().equals(CANCELADO))
            .filter(l -> passaNoFiltro(l, status))
            .toList();
        var cancelados = semFiltro(status)
            ? todos.stream().filter(l -> l.situacao().equals(CANCELADO)).toList()
            : List.<Lancamento>of();

        try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var e = new Estilos(wb);
            var aba = wb.createSheet(ABA);
            aba.setDisplayGridlines(false);
            int[] larguras = {14, 21, 13, 27, 15, 18, 3};
            for (int i = 0; i < larguras.length; i++) aba.setColumnWidth(i, larguras[i] * 256);

            cabecalho(aba, e, legenda(empresaId, lojaId, de, ate, tipo, status));
            int fim = tabela(aba, e, ativos);
            cartoes(aba, e, fim, ativos);
            int linha = total(aba, e, fim, ativos);
            blocoCancelados(aba, e, cancelados, linha + 3);
            if (!ativos.isEmpty()) graficos(wb, aba, ativos, de, ate, fim);

            var ps = aba.getPrintSetup();
            ps.setLandscape(true);
            ps.setFitWidth((short) 1);
            ps.setFitHeight((short) 0);
            aba.setFitToPage(true);
            aba.setHorizontallyCenter(true);
            aba.setRepeatingRows(CellRangeAddress.valueOf("9:9"));
            wb.setForceFormulaRecalculation(true);

            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── Dados ────────────────────────────────────────────────────────────────

    private List<Lancamento> carregar(UUID empresaId, UUID lojaId, LocalDate de, LocalDate ate, String tipo) {
        var lista = new ArrayList<Lancamento>();
        if (incluiTipo(tipo, "BOLETO")) adicionar(lista, "Boleto", boletoRepository.listarParaCsv(empresaId, lojaId, de, ate));
        if (incluiTipo(tipo, "PIX")) adicionar(lista, "PIX", pagamentoPixRepository.listarParaCsv(empresaId, lojaId, de, ate));
        if (incluiTipo(tipo, "CHEQUE")) adicionar(lista, "Cheque", chequeRepository.listarParaCsv(empresaId, lojaId, de, ate));
        lista.sort(Comparator.comparing(Lancamento::vencimento).thenComparing(Lancamento::loja));
        return lista;
    }

    private void adicionar(List<Lancamento> lista, String tipo, List<Object[]> rows) {
        for (var r : rows) {
            var valor = r[1] != null ? (BigDecimal) r[1] : BigDecimal.ZERO;
            var venc = r[2] instanceof LocalDate d ? d : LocalDate.parse(r[2].toString());
            lista.add(new Lancamento(venc, str(r[4]), tipo, str(r[0]), situacao(r[3].toString()), valor));
        }
    }

    /** Cheque compensado é pago; devolvido é vencido — mesma regra da tela. */
    private static String situacao(String status) {
        return switch (status) {
            case "PAGO", "COMPENSADO" -> PAGO;
            case "VENCIDO", "DEVOLVIDO" -> VENCIDO;
            case "CANCELADO" -> CANCELADO;
            default -> PENDENTE;
        };
    }

    private static boolean incluiTipo(String tipo, String este) {
        return tipo == null || tipo.equalsIgnoreCase("TODOS") || tipo.equalsIgnoreCase(este);
    }

    private static boolean semFiltro(String status) {
        return status == null || status.equalsIgnoreCase("TODOS");
    }

    /** "Pendentes" na tela traz pendente e vencido. */
    private static boolean passaNoFiltro(Lancamento l, String status) {
        if (semFiltro(status)) return true;
        if (status.equalsIgnoreCase("PAGO")) return l.situacao().equals(PAGO);
        if (status.equalsIgnoreCase("PENDENTE")) return !l.situacao().equals(PAGO);
        return true;
    }

    private String legenda(UUID empresaId, UUID lojaId, LocalDate de, LocalDate ate, String tipo, String status) {
        // A loja só aparece pelo nome se for da empresa de quem baixa.
        String loja = lojaId == null ? "Todas" : lojaRepository.findById(lojaId)
            .filter(l -> l.getEmpresa() != null && empresaId.equals(l.getEmpresa().getId()))
            .map(l -> l.getNome())
            .orElse("—");
        String nomeTipo = tipo == null ? "Todos" : switch (tipo.toUpperCase()) {
            case "BOLETO" -> "Boletos";
            case "PIX" -> "PIX";
            case "CHEQUE" -> "Cheques";
            default -> "Todos";
        };
        String nomeSituacao = semFiltro(status) ? "Todas"
            : status.equalsIgnoreCase("PAGO") ? "Pagos" : "Pendentes e vencidos";
        return "Período: " + de.format(DATA) + " a " + ate.format(DATA)
            + "   ·   Loja: " + loja
            + "   ·   Tipo: " + nomeTipo
            + "   ·   Situação: " + nomeSituacao
            + "   ·   Gerado em " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"));
    }

    // ── Planilha ─────────────────────────────────────────────────────────────

    private void cabecalho(XSSFSheet aba, Estilos e, String legenda) {
        float[] alturas = {33.75f, 19.5f, 7.5f, 18f, 30f, 4.5f, 27.75f, 9.75f, 25.5f};
        for (int i = 0; i < alturas.length; i++) aba.createRow(i).setHeightInPoints(alturas[i]);

        celula(aba, 0, 0, "Relatório de Pagamentos", e.titulo);
        celula(aba, 1, 0, legenda, e.legenda);
        for (int r = 0; r <= 1; r++) {
            for (int c = 1; c <= ULTIMA_COL_FAIXA; c++) aba.getRow(r).createCell(c).setCellStyle(e.faixa);
            aba.addMergedRegion(new CellRangeAddress(r, r, 0, ULTIMA_COL_FAIXA));
        }

        celula(aba, 6, 0, "A pagar  (pendente + vencido)", e.aPagarRotulo);
        for (int c = 1; c <= 5; c++) aba.getRow(6).createCell(c).setCellStyle(e.faixa);
        aba.getRow(6).getCell(3).setCellStyle(e.aPagarValor);
        aba.addMergedRegion(new CellRangeAddress(6, 6, 0, 2));
        aba.addMergedRegion(new CellRangeAddress(6, 6, 3, 5));
        celula(aba, 7, 0, "Os números acima e os gráficos seguem o filtro da tabela.", e.aviso);

        String[] colunas = {"Vencimento", "Loja", "Tipo", "Fornecedor", "Situação", "Valor"};
        for (int i = 0; i < colunas.length; i++) {
            celula(aba, 8, i, colunas[i], i == 0 || i == 4 ? e.cabCentro : i == 5 ? e.cabDireita : e.cabEsquerda);
        }
    }

    /** Devolve a última linha (base 0) da tabela. */
    private int tabela(XSSFSheet aba, Estilos e, List<Lancamento> ativos) {
        if (ativos.isEmpty()) {
            celula(aba, INI, 0, "Nenhum lançamento no período.", e.aviso);
            aba.createFreezePane(0, INI);
            return INI;
        }
        int r = INI;
        for (var l : ativos) {
            var row = aba.createRow(r++);
            row.setHeightInPoints(21);
            var data = row.createCell(0);
            data.setCellValue(l.vencimento());
            data.setCellStyle(e.data);
            celula(aba, row.getRowNum(), 1, l.loja(), e.texto);
            celula(aba, row.getRowNum(), 2, l.tipo(), e.texto);
            celula(aba, row.getRowNum(), 3, l.fornecedor(), e.texto);
            celula(aba, row.getRowNum(), 4, l.situacao(), e.situacao.get(l.situacao()));
            var valor = row.createCell(5);
            valor.setCellValue(l.valor().doubleValue());
            valor.setCellStyle(e.valor);
        }
        int fim = r - 1;
        aba.setAutoFilter(new CellRangeAddress(8, fim, 0, 5));
        aba.createFreezePane(0, INI);
        return fim;
    }

    private void cartoes(XSSFSheet aba, Estilos e, int fim, List<Lancamento> ativos) {
        formula(aba.getRow(6).getCell(3), "C5+E5", soma(ativos, l -> !l.situacao().equals(PAGO)));
        for (int i = 0; i < SITUACOES.size(); i++) {
            var s = SITUACOES.get(i);
            int c = i * 2;
            var rotulo = aba.getRow(3).createCell(c);
            rotulo.setCellValue(s.toUpperCase());
            rotulo.setCellStyle(e.cartaoRotulo.get(s));
            aba.getRow(3).createCell(c + 1).setCellStyle(e.cartaoRotulo.get(s));
            var valor = aba.getRow(4).createCell(c);
            formula(valor, somaVisivel("", fim, "$E$" + (INI + 1) + ":$E$" + (fim + 1) + "=\"" + s + "\""),
                soma(ativos, l -> l.situacao().equals(s)));
            valor.setCellStyle(e.cartaoValor.get(s));
            aba.getRow(4).createCell(c + 1).setCellStyle(e.cartaoValor.get(s));
            aba.addMergedRegion(new CellRangeAddress(3, 3, c, c + 1));
            aba.addMergedRegion(new CellRangeAddress(4, 4, c, c + 1));
        }
    }

    private int total(XSSFSheet aba, Estilos e, int fim, List<Lancamento> ativos) {
        int t = fim + 1;
        var row = aba.createRow(t);
        row.setHeightInPoints(25.5f);
        for (int c = 0; c <= 5; c++) row.createCell(c).setCellStyle(c == 5 ? e.totalValor : e.totalRotulo);
        row.getCell(0).setCellValue("Total (só as linhas visíveis no filtro)");
        formula(row.getCell(5), "SUBTOTAL(109,F" + (INI + 1) + ":F" + (fim + 1) + ")", soma(ativos, l -> true));
        return t;
    }

    private void blocoCancelados(XSSFSheet aba, Estilos e, List<Lancamento> cancelados, int r) {
        if (cancelados.isEmpty()) return;
        int n = cancelados.size();
        celula(aba, r, 0, "Cancelados — não entram em nenhum total (" + n + (n == 1 ? " lançamento)" : " lançamentos)"), e.canceladoTitulo);
        aba.addMergedRegion(new CellRangeAddress(r, r, 0, 5));
        for (var l : cancelados) {
            var row = aba.createRow(++r);
            var data = row.createCell(0);
            data.setCellValue(l.vencimento());
            data.setCellStyle(e.canceladoData);
            celula(aba, r, 1, l.loja(), e.cancelado);
            celula(aba, r, 2, l.tipo(), e.cancelado);
            celula(aba, r, 3, l.fornecedor(), e.cancelado);
            celula(aba, r, 4, CANCELADO, e.cancelado);
            var valor = row.createCell(5);
            valor.setCellValue(l.valor().doubleValue());
            valor.setCellStyle(e.canceladoValor);
        }
    }

    /**
     * Soma a coluna Valor só nas linhas visíveis do filtro e que atendem à condição.
     * {@code SUBTOTAL(109, OFFSET(...))} devolve, linha a linha, o valor se ela
     * está visível e 0 se o filtro a escondeu.
     */
    private static String somaVisivel(String prefixoAba, int fim, String condicao) {
        String f1 = prefixoAba + "$F$" + (INI + 1);
        String faixa = prefixoAba + "$F$" + (INI + 1) + ":$F$" + (fim + 1);
        return "SUMPRODUCT(SUBTOTAL(109,OFFSET(" + f1 + ",ROW(" + faixa + ")-ROW(" + f1 + "),0))*(" + condicao + "))";
    }

    // ── Gráficos ─────────────────────────────────────────────────────────────

    private void graficos(XSSFWorkbook wb, XSSFSheet aba, List<Lancamento> ativos, LocalDate de, LocalDate ate, int fim) {
        var dados = wb.createSheet(ABA_DADOS);
        wb.setSheetHidden(wb.getSheetIndex(dados), true);
        String L = "'" + ABA + "'!";
        String colE = L + "$E$" + (INI + 1) + ":$E$" + (fim + 1);
        String colB = L + "$B$" + (INI + 1) + ":$B$" + (fim + 1);
        String colA = L + "$A$" + (INI + 1) + ":$A$" + (fim + 1);
        var desenho = aba.createDrawingPatriarch();
        int linha = INI;

        // 1. Rosca: pago / pendente / vencido
        dados.createRow(0).createCell(0).setCellValue("Situação");
        dados.getRow(0).createCell(1).setCellValue("Valor");
        var porSituacao = new Double[SITUACOES.size()];
        for (int i = 0; i < SITUACOES.size(); i++) {
            var s = SITUACOES.get(i);
            var row = dados.createRow(i + 1);
            row.createCell(0).setCellValue(s);
            porSituacao[i] = soma(ativos, l -> l.situacao().equals(s));
            formula(row.createCell(1), somaVisivel(L, fim, colE + "=\"" + s + "\""), porSituacao[i]);
        }
        rosca(novoGrafico(desenho, linha, linha + 11, "Para onde foi o dinheiro"), dados, porSituacao);
        linha += 11;

        // 2. Barras: a pagar por loja, a que mais deve em cima (só faz sentido com mais de uma loja)
        var aPagar = new HashMap<String, BigDecimal>();
        for (var l : ativos) {
            aPagar.merge(l.loja(), l.situacao().equals(PAGO) ? BigDecimal.ZERO : l.valor(), BigDecimal::add);
        }
        if (aPagar.size() > 1) {
            var lojas = aPagar.keySet().stream().sorted(Comparator.comparing(aPagar::get)).toList();
            var series = List.of(PENDENTE, VENCIDO);
            int r0 = 5;
            var cab = dados.createRow(r0);
            cab.createCell(0).setCellValue("Loja");
            var valores = new ArrayList<Double[]>();
            for (int j = 0; j < series.size(); j++) {
                cab.createCell(j + 1).setCellValue(series.get(j));
                valores.add(new Double[lojas.size()]);
            }
            for (int i = 0; i < lojas.size(); i++) {
                var loja = lojas.get(i);
                var row = dados.createRow(r0 + 1 + i);
                row.createCell(0).setCellValue(loja);
                String daLoja = "(" + colB + "=$A$" + (r0 + 2 + i) + ")";
                for (int j = 0; j < series.size(); j++) {
                    var s = series.get(j);
                    valores.get(j)[i] = soma(ativos, l -> l.loja().equals(loja) && l.situacao().equals(s));
                    formula(row.createCell(j + 1), somaVisivel(L, fim, daLoja + "*(" + colE + "=\"" + s + "\")"), valores.get(j)[i]);
                }
            }
            barras(novoGrafico(desenho, linha, linha + 14, "A pagar por loja"), dados,
                r0, lojas.toArray(String[]::new), series, valores, true, null);
            linha += 14;
        }

        // 3. Colunas: quando o dinheiro vence — por semana, ou por mês em períodos longos
        boolean porSemana = ChronoUnit.DAYS.between(de, ate) < 70;
        var faixas = porSemana ? semanas(de, ate) : meses(de, ate);
        int r0 = dados.getLastRowNum() + 3;
        var cab = dados.createRow(r0);
        cab.createCell(0).setCellValue(porSemana ? "Semana" : "Mês");
        var rotulos = new String[faixas.size()];
        var valores = new ArrayList<Double[]>();
        for (int j = 0; j < SITUACOES.size(); j++) {
            cab.createCell(j + 1).setCellValue(SITUACOES.get(j));
            valores.add(new Double[faixas.size()]);
        }
        for (int i = 0; i < faixas.size(); i++) {
            var f = faixas.get(i);
            var row = dados.createRow(r0 + 1 + i);
            rotulos[i] = porSemana
                ? f[0].format(DIA_MES) + " a " + f[1].format(DIA_MES)
                : f[0].format(DateTimeFormatter.ofPattern("MM/yyyy"));
            row.createCell(0).setCellValue(rotulos[i]);
            String naFaixa = "(" + colA + ">=" + dataExcel(f[0]) + ")*(" + colA + "<=" + dataExcel(f[1]) + ")";
            for (int j = 0; j < SITUACOES.size(); j++) {
                var s = SITUACOES.get(j);
                valores.get(j)[i] = soma(ativos, l -> !l.vencimento().isBefore(f[0]) && !l.vencimento().isAfter(f[1]) && l.situacao().equals(s));
                formula(row.createCell(j + 1), somaVisivel(L, fim, naFaixa + "*(" + colE + "=\"" + s + "\")"), valores.get(j)[i]);
            }
        }
        barras(novoGrafico(desenho, linha, linha + 11, porSemana ? "Vencimentos por semana" : "Vencimentos por mês"), dados,
            r0, rotulos, SITUACOES, valores, false, "Vencimento");
    }

    private static double soma(List<Lancamento> ls, java.util.function.Predicate<Lancamento> filtro) {
        return ls.stream().filter(filtro).map(Lancamento::valor).reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue();
    }

    /**
     * Grava a fórmula e, junto, o valor já calculado: quem abre sem recalcular
     * (pré-visualização do celular, do e-mail) vê o número certo, não zero.
     */
    private static void formula(Cell c, String f, double valor) {
        c.setCellFormula(f);
        c.setCellValue(valor);
    }

    /**
     * Furo da rosca, espaço e sobreposição das barras são porcentagens cujo tipo
     * o poi-ooxml-lite não traz (os setters do POI quebram em produção). Grava o
     * atributo {@code val} direto no XML.
     */
    private static void valorCru(org.apache.xmlbeans.XmlObject elemento, String valor) {
        try (var cursor = elemento.newCursor()) {
            cursor.setAttributeText(new javax.xml.namespace.QName("val"), valor);
        }
    }

    private static String referencia(XSSFSheet aba, int linha1, int linha2, int coluna) {
        return new CellRangeAddress(linha1, linha2, coluna, coluna).formatAsString(aba.getSheetName(), true);
    }

    private static List<LocalDate[]> semanas(LocalDate de, LocalDate ate) {
        var r = new ArrayList<LocalDate[]>();
        for (var d = de; !d.isAfter(ate); d = d.plusDays(7)) {
            var f = d.plusDays(6);
            r.add(new LocalDate[]{d, f.isAfter(ate) ? ate : f});
        }
        return r;
    }

    private static List<LocalDate[]> meses(LocalDate de, LocalDate ate) {
        var r = new ArrayList<LocalDate[]>();
        for (var d = de; !d.isAfter(ate); d = d.withDayOfMonth(1).plusMonths(1)) {
            var f = d.withDayOfMonth(d.lengthOfMonth());
            r.add(new LocalDate[]{d, f.isAfter(ate) ? ate : f});
        }
        return r;
    }

    private static String dataExcel(LocalDate d) {
        return "DATE(" + d.getYear() + "," + d.getMonthValue() + "," + d.getDayOfMonth() + ")";
    }

    private XSSFChart novoGrafico(XSSFDrawing desenho, int linhaIni, int linhaFim, String titulo) {
        var ancora = desenho.createAnchor(0, 0, 0, 0, COL_GRAFICO, linhaIni, COL_GRAFICO + 8, linhaFim);
        // Preso às células, o gráfico encolhe quando o filtro esconde as linhas que ele cobre.
        ancora.setAnchorType(ClientAnchor.AnchorType.DONT_MOVE_AND_RESIZE);
        var grafico = desenho.createChart(ancora);
        grafico.setTitleText(titulo);
        // Sem overlay explícito o Excel desenha título e legenda por cima das barras.
        grafico.setTitleOverlay(false);
        var legenda = grafico.getOrAddLegend();
        legenda.setPosition(LegendPosition.BOTTOM);
        legenda.setOverlay(false);
        // Os dados estão numa aba oculta.
        grafico.setPlotOnlyVisibleCells(false);
        return grafico;
    }

    // Os gráficos levam os valores já calculados e apontam para as células da
    // aba de dados: o Excel recalcula ao abrir e ao filtrar.
    private void rosca(XSSFChart grafico, XSSFSheet dados, Double[] valores) {
        var cat = XDDFDataSourcesFactory.fromArray(SITUACOES.toArray(String[]::new), referencia(dados, 1, 3, 0));
        var val = XDDFDataSourcesFactory.fromArray(valores, referencia(dados, 1, 3, 1));
        var rosca = (XDDFDoughnutChartData) grafico.createData(ChartTypes.DOUGHNUT, null, null);
        rosca.setVaryColors(true);
        var serie = (XDDFDoughnutChartData.Series) rosca.addSeries(cat, val);
        serie.setTitle("Valor", null);
        grafico.plot(rosca);
        var dn = grafico.getCTChart().getPlotArea().getDoughnutChartArray(0);
        valorCru(dn.isSetHoleSize() ? dn.getHoleSize() : dn.addNewHoleSize(), "58");

        var ser = serie.getCTPieSer();
        for (int i = 0; i < SITUACOES.size(); i++) {
            CTDPt ponto = ser.addNewDPt();
            ponto.addNewIdx().setVal(i);
            CTShapeProperties sp = ponto.addNewSpPr();
            sp.addNewSolidFill().addNewSrgbClr().setVal(rgb(COR.get(SITUACOES.get(i))[0]));
            sp.addNewLn().addNewSolidFill().addNewSrgbClr().setVal(rgb("FFFFFF"));
        }
        CTDLbls rotulos = ser.isSetDLbls() ? ser.getDLbls() : ser.addNewDLbls();
        rotulos.addNewShowLegendKey().setVal(false);
        rotulos.addNewShowVal().setVal(false);
        rotulos.addNewShowCatName().setVal(false);
        rotulos.addNewShowSerName().setVal(false);
        rotulos.addNewShowPercent().setVal(true);
        rotulos.addNewShowBubbleSize().setVal(false);
    }

    private void barras(XSSFChart grafico, XSSFSheet dados, int linhaCab, String[] categorias, List<String> series,
                        List<Double[]> valores, boolean deitado, String tituloEixo) {
        int n = categorias.length;
        var eixoCat = grafico.createCategoryAxis(deitado ? AxisPosition.LEFT : AxisPosition.BOTTOM);
        var eixoVal = grafico.createValueAxis(deitado ? AxisPosition.BOTTOM : AxisPosition.LEFT);
        eixoVal.setCrosses(AxisCrosses.AUTO_ZERO);
        eixoVal.setCrossBetween(AxisCrossBetween.BETWEEN);
        eixoVal.setNumberFormat("\"R$\" #,##0");
        if (!deitado) {
            var grade = eixoVal.getOrAddMajorGridProperties();
            grade.setLineProperties(new XDDFLineProperties(new XDDFSolidFillProperties(XDDFColor.from(rgb("ECEFF4")))));
        }
        if (tituloEixo != null) eixoCat.setTitle(tituloEixo);

        var dadosBarras = (XDDFBarChartData) grafico.createData(ChartTypes.BAR, eixoCat, eixoVal);
        dadosBarras.setBarDirection(deitado ? BarDirection.BAR : BarDirection.COL);
        dadosBarras.setBarGrouping(BarGrouping.STACKED);
        dadosBarras.setVaryColors(false);

        var cat = XDDFDataSourcesFactory.fromArray(categorias, referencia(dados, linhaCab + 1, linhaCab + n, 0));
        for (int j = 0; j < series.size(); j++) {
            var val = XDDFDataSourcesFactory.fromArray(valores.get(j), referencia(dados, linhaCab + 1, linhaCab + n, j + 1));
            var serie = dadosBarras.addSeries(cat, val);
            serie.setTitle(series.get(j), new org.apache.poi.ss.util.CellReference(dados.getSheetName(), linhaCab, j + 1, true, true));
            var forma = new XDDFShapeProperties();
            forma.setFillProperties(new XDDFSolidFillProperties(XDDFColor.from(rgb(COR.get(series.get(j))[0]))));
            forma.setLineProperties(new XDDFLineProperties(new XDDFNoFillProperties()));
            serie.setShapeProperties(forma);
        }
        grafico.plot(dadosBarras);
        // Empilhadas de verdade: uma barra por cima da outra (overlap 100).
        var bc = grafico.getCTChart().getPlotArea().getBarChartArray(0);
        valorCru(bc.isSetGapWidth() ? bc.getGapWidth() : bc.addNewGapWidth(), deitado ? "45" : "60");
        valorCru(bc.isSetOverlap() ? bc.getOverlap() : bc.addNewOverlap(), "100");

        // O título do eixo também precisa de overlay explícito.
        var plot = grafico.getCTChart().getPlotArea();
        for (var ax : plot.getCatAxArray()) {
            if (ax.isSetTitle()) {
                var t = ax.getTitle();
                if (t.isSetOverlay()) t.getOverlay().setVal(false); else t.addNewOverlay().setVal(false);
            }
        }
    }

    // ── Estilos ──────────────────────────────────────────────────────────────

    /** Um estilo por tipo de célula — criar um por célula estoura o limite do Excel. */
    private static final class Estilos {
        final XSSFCellStyle titulo, legenda, faixa, aPagarRotulo, aPagarValor, aviso;
        final XSSFCellStyle cabEsquerda, cabCentro, cabDireita, texto, data, valor;
        final XSSFCellStyle totalRotulo, totalValor, canceladoTitulo, cancelado, canceladoData, canceladoValor;
        final Map<String, XSSFCellStyle> situacao = new HashMap<>();
        final Map<String, XSSFCellStyle> cartaoRotulo = new HashMap<>();
        final Map<String, XSSFCellStyle> cartaoValor = new HashMap<>();
        private final XSSFWorkbook wb;

        Estilos(XSSFWorkbook wb) {
            this.wb = wb;
            short brl = wb.createDataFormat().getFormat(BRL);
            short dma = wb.createDataFormat().getFormat("dd/mm/yyyy");
            var linhaFina = cor("ECEFF4");

            faixa = estilo(fonte(10, false, "FFFFFF"), NAVY, null, null);
            titulo = estilo(fonte(18, true, "FFFFFF"), NAVY, null, null); titulo.setIndention((short) 1);
            legenda = estilo(fonte(9, false, "B8C2D6"), NAVY, null, null); legenda.setIndention((short) 1);
            aPagarRotulo = estilo(fonte(10, true, "FFFFFF"), NAVY, null, null); aPagarRotulo.setIndention((short) 1);
            aPagarValor = estilo(fonte(15, true, "FFFFFF"), NAVY, HorizontalAlignment.RIGHT, null);
            aPagarValor.setDataFormat(brl); aPagarValor.setIndention((short) 1);
            aviso = estilo(fonte(8, false, "7A8499"), null, null, null);

            cabEsquerda = estilo(fonte(10, true, "FFFFFF"), NAVY, HorizontalAlignment.LEFT, null); cabEsquerda.setIndention((short) 1);
            cabCentro = estilo(fonte(10, true, "FFFFFF"), NAVY, HorizontalAlignment.CENTER, null);
            cabDireita = estilo(fonte(10, true, "FFFFFF"), NAVY, HorizontalAlignment.RIGHT, null); cabDireita.setIndention((short) 1);

            texto = estilo(fonte(10, false, TEXTO), null, HorizontalAlignment.LEFT, linhaFina); texto.setIndention((short) 1);
            data = estilo(fonte(10, false, TEXTO), null, HorizontalAlignment.CENTER, linhaFina); data.setDataFormat(dma);
            valor = estilo(fonte(10, true, TEXTO), null, HorizontalAlignment.RIGHT, linhaFina);
            valor.setDataFormat(brl); valor.setIndention((short) 1);
            for (var s : SITUACOES) {
                situacao.put(s, estilo(fonte(10, true, COR.get(s)[2]), null, HorizontalAlignment.CENTER, linhaFina));
                var rot = estilo(fonte(8, true, COR.get(s)[2]), COR.get(s)[1], HorizontalAlignment.LEFT, null);
                rot.setIndention((short) 1);
                rot.setBorderTop(BorderStyle.THICK);
                rot.setTopBorderColor(cor(COR.get(s)[0]));
                cartaoRotulo.put(s, rot);
                var val = estilo(fonte(16, true, COR.get(s)[2]), COR.get(s)[1], HorizontalAlignment.LEFT, null);
                val.setDataFormat(brl); val.setIndention((short) 1);
                cartaoValor.put(s, val);
            }

            totalRotulo = estilo(fonte(10, true, NAVY), "EEF1F7", HorizontalAlignment.LEFT, null);
            totalRotulo.setIndention((short) 1);
            totalRotulo.setBorderTop(BorderStyle.MEDIUM); totalRotulo.setTopBorderColor(cor(NAVY));
            totalValor = estilo(fonte(10, true, NAVY), "EEF1F7", HorizontalAlignment.RIGHT, null);
            totalValor.setDataFormat(brl); totalValor.setIndention((short) 1);
            totalValor.setBorderTop(BorderStyle.MEDIUM); totalValor.setTopBorderColor(cor(NAVY));

            canceladoTitulo = estilo(fonte(10, true, "6B7280"), null, null, null);
            cancelado = estilo(fonte(9, false, "9AA3B2"), null, HorizontalAlignment.LEFT, null); cancelado.setIndention((short) 1);
            canceladoData = estilo(fonte(9, false, "9AA3B2"), null, HorizontalAlignment.CENTER, null); canceladoData.setDataFormat(dma);
            canceladoValor = estilo(fonte(9, false, "9AA3B2"), null, HorizontalAlignment.RIGHT, null);
            canceladoValor.setDataFormat(brl); canceladoValor.setIndention((short) 1);
        }

        private XSSFFont fonte(double tamanho, boolean negrito, String cor) {
            var f = wb.createFont();
            f.setFontName("Arial");
            f.setFontHeight(tamanho);
            f.setBold(negrito);
            f.setColor(cor(cor));
            return f;
        }

        private XSSFCellStyle estilo(XSSFFont fonte, String fundo, HorizontalAlignment alinhamento, XSSFColor linhaBaixo) {
            var s = wb.createCellStyle();
            s.setFont(fonte);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            if (alinhamento != null) s.setAlignment(alinhamento);
            if (fundo != null) {
                s.setFillForegroundColor(cor(fundo));
                s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            if (linhaBaixo != null) {
                s.setBorderBottom(BorderStyle.THIN);
                s.setBottomBorderColor(linhaBaixo);
            }
            return s;
        }
    }

    private static void celula(XSSFSheet aba, int linha, int coluna, String valor, CellStyle estilo) {
        var row = aba.getRow(linha) != null ? aba.getRow(linha) : aba.createRow(linha);
        var c = row.createCell(coluna);
        c.setCellValue(valor);
        c.setCellStyle(estilo);
    }

    private static XSSFColor cor(String hex) {
        return new XSSFColor(rgb(hex), null);
    }

    private static byte[] rgb(String hex) {
        return new byte[]{
            (byte) Integer.parseInt(hex.substring(0, 2), 16),
            (byte) Integer.parseInt(hex.substring(2, 4), 16),
            (byte) Integer.parseInt(hex.substring(4, 6), 16)};
    }

    private static String str(Object o) {
        return o != null ? o.toString() : "";
    }
}
