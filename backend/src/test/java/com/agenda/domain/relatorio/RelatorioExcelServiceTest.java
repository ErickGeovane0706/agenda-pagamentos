package com.agenda.domain.relatorio;

import com.agenda.domain.boleto.BoletoRepository;
import com.agenda.domain.cheque.ChequeRepository;
import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.loja.Loja;
import com.agenda.domain.loja.LojaRepository;
import com.agenda.domain.pix.PagamentoPixRepository;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * O Excel do relatório segue o modelo aprovado (MODELO A4): uma tabela só com a
 * coluna Loja, valores e datas de verdade, cancelados fora de qualquer total,
 * cartões e gráficos que somam só as linhas visíveis no filtro do Excel, e os
 * filtros da tela (loja, tipo, situação) decidindo o que entra no arquivo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RelatorioExcelServiceTest {

    @Mock private BoletoRepository boletoRepository;
    @Mock private PagamentoPixRepository pixRepository;
    @Mock private ChequeRepository chequeRepository;
    @Mock private LojaRepository lojaRepository;

    private RelatorioExcelService service;
    private final UUID empresaId = UUID.randomUUID();
    private final LocalDate de = LocalDate.of(2026, 10, 1);
    private final LocalDate ate = LocalDate.of(2026, 10, 31);

    @BeforeEach
    void setUp() {
        service = new RelatorioExcelService(boletoRepository, pixRepository, chequeRepository, lojaRepository);
        when(boletoRepository.listarParaCsv(any(), any(), any(), any())).thenReturn(linhas(
            new Object[]{"Ambev", bd("1000.00"), LocalDate.of(2026, 10, 5), "PAGO", "Centro"},
            new Object[]{"Vivo", bd("300.00"), LocalDate.of(2026, 10, 2), "VENCIDO", "Norte"},
            new Object[]{"Sabesp", bd("999.00"), LocalDate.of(2026, 10, 3), "CANCELADO", "Centro"},
            new Object[]{"Seara", bd("500.00"), LocalDate.of(2026, 10, 20), "PENDENTE", "Norte"}));
        when(pixRepository.listarParaCsv(any(), any(), any(), any())).thenReturn(linhas(
            new Object[]{"Aluguel", bd("200.00"), LocalDate.of(2026, 10, 10), "PAGO", "Norte"}));
        when(chequeRepository.listarParaCsv(any(), any(), any(), any())).thenReturn(linhas(
            new Object[]{"BRF", bd("50.00"), LocalDate.of(2026, 10, 15), "DEVOLVIDO", "Centro"},
            new Object[]{"JBS", bd("80.00"), LocalDate.of(2026, 10, 25), "COMPENSADO", "Centro"}));
    }

    @Test
    void tabela_temLoja_valorEDataDeVerdade_emOrdemDeVencimento_eSemCancelado() throws Exception {
        var aba = gerar(null, null, null).getSheet(RelatorioExcelService.ABA);

        assertEquals(List.of("Vencimento", "Loja", "Tipo", "Fornecedor", "Situação", "Valor"), textos(aba.getRow(8), 6));
        var tabela = linhasDaTabela(aba);
        assertEquals(6, tabela.size(), "cancelado não entra na tabela");
        assertEquals("Vivo", tabela.get(0).getCell(3).getStringCellValue());
        assertEquals("Norte", tabela.get(0).getCell(1).getStringCellValue());
        assertEquals("Boleto", tabela.get(0).getCell(2).getStringCellValue());
        assertEquals("Vencido", tabela.get(0).getCell(4).getStringCellValue());
        assertTrue(DateUtil.isCellDateFormatted(tabela.get(0).getCell(0)));
        assertEquals(LocalDate.of(2026, 10, 2), tabela.get(0).getCell(0).getLocalDateTimeCellValue().toLocalDate());
        assertEquals(CellType.NUMERIC, tabela.get(0).getCell(5).getCellType());
        assertEquals(300.0, tabela.get(0).getCell(5).getNumericCellValue());

        var cheque = tabela.stream().filter(r -> r.getCell(3).getStringCellValue().equals("BRF")).findFirst().orElseThrow();
        assertEquals("Vencido", cheque.getCell(4).getStringCellValue(), "cheque devolvido conta como vencido");
        var compensado = tabela.stream().filter(r -> r.getCell(3).getStringCellValue().equals("JBS")).findFirst().orElseThrow();
        assertEquals("Pago", compensado.getCell(4).getStringCellValue(), "cheque compensado conta como pago");
    }

    @Test
    void cartoesETotal_somamSoAsLinhasVisiveis_eBatemComOsValores() throws Exception {
        var wb = gerar(null, null, null);
        var aba = wb.getSheet(RelatorioExcelService.ABA);

        assertTrue(aba.getRow(4).getCell(0).getCellFormula().contains("SUBTOTAL(109,OFFSET("), "cartão segue o filtro do Excel");
        // valor gravado no arquivo (o que a pré-visualização do celular mostra) e o que a fórmula calcula
        confere(wb, aba.getRow(4).getCell(0), 1280.0);  // pago: 1000 + 200 + 80
        confere(wb, aba.getRow(4).getCell(2), 500.0);   // pendente
        confere(wb, aba.getRow(4).getCell(4), 350.0);   // vencido: 300 + 50
        assertEquals("C5+E5", aba.getRow(6).getCell(3).getCellFormula());
        assertEquals(850.0, aba.getRow(6).getCell(3).getNumericCellValue(), 0.001);  // a pagar

        var total = aba.getRow(9 + 6);
        assertTrue(total.getCell(5).getCellFormula().startsWith("SUBTOTAL(109,"));
        confere(wb, total.getCell(5), 2130.0);
    }

    @Test
    void dadosDosGraficos_porLojaEPorSemana_batemComOsLancamentos() throws Exception {
        var wb = gerar(null, null, null);
        var dados = wb.getSheet(RelatorioExcelService.ABA_DADOS);

        // por loja, a que mais deve por último (fica em cima no gráfico deitado)
        assertEquals("Centro", dados.getRow(6).getCell(0).getStringCellValue());
        confere(wb, dados.getRow(6).getCell(2), 50.0);    // Centro vencido: cheque devolvido
        assertEquals("Norte", dados.getRow(7).getCell(0).getStringCellValue());
        confere(wb, dados.getRow(7).getCell(1), 500.0);   // Norte pendente
        confere(wb, dados.getRow(7).getCell(2), 300.0);   // Norte vencido

        // semanas a partir do início do período: 01/10 a 07/10 tem o Vivo vencido e a Ambev paga
        int r0 = 10;
        assertEquals("01/10 a 07/10", dados.getRow(r0 + 1).getCell(0).getStringCellValue());
        confere(wb, dados.getRow(r0 + 1).getCell(1), 1000.0);
        confere(wb, dados.getRow(r0 + 1).getCell(3), 300.0);
        assertEquals("29/10 a 31/10", dados.getRow(r0 + 5).getCell(0).getStringCellValue());
    }

    @Test
    void cancelados_ficamNumBlocoAParte_quandoNaoHaFiltroDeSituacao() throws Exception {
        var aba = gerar(null, null, null).getSheet(RelatorioExcelService.ABA);

        assertTrue(textoDaAba(aba).contains("Cancelados — não entram em nenhum total (1 lançamento)"));
        assertTrue(textoDaAba(aba).contains("Sabesp"));

        var soPagos = gerar(null, null, "PAGO").getSheet(RelatorioExcelService.ABA);
        assertFalse(textoDaAba(soPagos).contains("Sabesp"));
    }

    @Test
    void filtroDeTipo_soConsultaOTipoEscolhido() throws Exception {
        var aba = gerar(null, "CHEQUE", null).getSheet(RelatorioExcelService.ABA);

        assertEquals(2, linhasDaTabela(aba).size());
        verify(boletoRepository, never()).listarParaCsv(any(), any(), any(), any());
        verify(pixRepository, never()).listarParaCsv(any(), any(), any(), any());
        assertTrue(aba.getRow(1).getCell(0).getStringCellValue().contains("Tipo: Cheques"));
    }

    @Test
    void filtroPendente_trazPendenteEVencido_comoNaTela() throws Exception {
        var aba = gerar(null, null, "PENDENTE").getSheet(RelatorioExcelService.ABA);

        var situacoes = linhasDaTabela(aba).stream().map(r -> r.getCell(4).getStringCellValue()).distinct().sorted().toList();
        assertEquals(List.of("Pendente", "Vencido"), situacoes);
        assertTrue(aba.getRow(1).getCell(0).getStringCellValue().contains("Situação: Pendentes e vencidos"));
    }

    @Test
    void downloadPorLoja_cabecalhoTemONomeDaLoja_eAsConsultasRecebemALoja() throws Exception {
        var centro = UUID.randomUUID();
        when(lojaRepository.findById(centro)).thenReturn(Optional.of(
            Loja.builder().id(centro).nome("Centro").empresa(Empresa.builder().id(empresaId).build()).build()));

        var aba = gerar(centro, null, null).getSheet(RelatorioExcelService.ABA);

        assertTrue(aba.getRow(1).getCell(0).getStringCellValue().contains("Loja: Centro"));
        verify(boletoRepository).listarParaCsv(empresaId, centro, de, ate);
        verify(pixRepository).listarParaCsv(empresaId, centro, de, ate);
        verify(chequeRepository).listarParaCsv(empresaId, centro, de, ate);
    }

    @Test
    void downloadPorLoja_deOutraEmpresa_naoMostraONomeDela() throws Exception {
        var intrusa = UUID.randomUUID();
        when(lojaRepository.findById(intrusa)).thenReturn(Optional.of(
            Loja.builder().id(intrusa).nome("Loja Alheia").empresa(Empresa.builder().id(UUID.randomUUID()).build()).build()));

        var aba = gerar(intrusa, null, null).getSheet(RelatorioExcelService.ABA);

        assertFalse(aba.getRow(1).getCell(0).getStringCellValue().contains("Loja Alheia"));
    }

    @Test
    void graficos_abaixoDaLinhaFixa_semTextoPorCimaDasBarras_eDadosNumaAbaOculta() throws Exception {
        var wb = gerar(null, null, null);
        var aba = wb.getSheet(RelatorioExcelService.ABA);

        assertEquals(9, aba.getPaneInformation().getHorizontalSplitPosition(), "linhas 1 a 9 fixas");
        assertEquals("A9:F15", aba.getCTWorksheet().getAutoFilter().getRef());

        List<XSSFChart> graficos = aba.getDrawingPatriarch().getCharts();
        assertEquals(List.of("Para onde foi o dinheiro", "A pagar por loja", "Vencimentos por semana"),
            graficos.stream().map(g -> g.getTitleText().getString()).toList());
        var molduras = aba.getDrawingPatriarch().getShapes().stream()
            .filter(s -> s instanceof org.apache.poi.xssf.usermodel.XSSFGraphicFrame).toList();
        assertEquals(3, molduras.size());
        for (var m : molduras) {
            var ancora = (org.apache.poi.xssf.usermodel.XSSFClientAnchor) m.getAnchor();
            assertTrue(ancora.getRow1() >= 9, "começa abaixo da área fixa");
            assertTrue(ancora.getCol1() >= 7, "fica à direita da tabela");
        }
        for (var g : graficos) {
            assertFalse(g.getTitleOverlay(), "título não sobrepõe");
            assertFalse(g.getCTChart().getLegend().getOverlay().getVal(), "legenda não sobrepõe");
            assertFalse(g.isPlotOnlyVisibleCells(), "dados estão numa aba oculta");
        }
        assertTrue(wb.isSheetHidden(wb.getSheetIndex(RelatorioExcelService.ABA_DADOS)));
    }

    @Test
    void comUmaLojaSo_naoTemGraficoPorLoja() throws Exception {
        when(boletoRepository.listarParaCsv(any(), any(), any(), any())).thenReturn(linhas(
            new Object[]{"Ambev", bd("1000.00"), LocalDate.of(2026, 10, 5), "PAGO", "Centro"}));
        when(pixRepository.listarParaCsv(any(), any(), any(), any())).thenReturn(List.of());
        when(chequeRepository.listarParaCsv(any(), any(), any(), any())).thenReturn(List.of());

        var graficos = gerar(null, null, null).getSheet(RelatorioExcelService.ABA).getDrawingPatriarch().getCharts();

        assertEquals(List.of("Para onde foi o dinheiro", "Vencimentos por semana"),
            graficos.stream().map(g -> g.getTitleText().getString()).toList());
    }

    @Test
    void semLancamento_naoQuebra_eAvisa() throws Exception {
        when(boletoRepository.listarParaCsv(any(), any(), any(), any())).thenReturn(List.of());
        when(pixRepository.listarParaCsv(any(), any(), any(), any())).thenReturn(List.of());
        when(chequeRepository.listarParaCsv(any(), any(), any(), any())).thenReturn(List.of());

        var aba = gerar(null, null, null).getSheet(RelatorioExcelService.ABA);

        assertEquals("Nenhum lançamento no período.", aba.getRow(9).getCell(0).getStringCellValue());
        assertNull(aba.getDrawingPatriarch());
    }

    /**
     * O POI não calcula SUBTOTAL(109, OFFSET(...)) dentro de SUMPRODUCT — o Excel
     * sim. Então confere o valor gravado e calcula a mesma fórmula sem a parte
     * das linhas visíveis (sem filtro, todas estão visíveis): prova que as
     * faixas e as condições estão certas.
     */
    private static void confere(XSSFWorkbook wb, org.apache.poi.ss.usermodel.Cell c, double esperado) {
        assertEquals(esperado, c.getNumericCellValue(), 0.001, "valor gravado em " + c.getAddress());
        String semVisiveis = c.getCellFormula().replaceAll(
            "SUBTOTAL\\(109,OFFSET\\(([^,]+),ROW\\(([^)]+)\\)-ROW\\([^)]+\\),0\\)\\)", "$2");
        // O POI também não multiplica faixas dentro do SUMPRODUCT; como fórmula
        // matricial SUM(...) ele calcula — é a mesma conta.
        var rascunho = (XSSFSheet) c.getSheet();  // na mesma aba: $A$7 da aba de dados é o nome da loja
        var celulas = rascunho.setArrayFormula(semVisiveis.replaceFirst("^SUMPRODUCT\\(", "SUM("),
            org.apache.poi.ss.util.CellRangeAddress.valueOf("Z500"));
        var calculado = wb.getCreationHelper().createFormulaEvaluator().evaluate(celulas.getTopLeftCell()).getNumberValue();
        assertEquals(esperado, calculado, 0.001, "fórmula de " + c.getAddress() + ": " + semVisiveis);
    }

    private XSSFWorkbook gerar(UUID lojaId, String tipo, String status) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(service.gerarExcel(empresaId, lojaId, de, ate, tipo, status)));
    }

    private List<Row> linhasDaTabela(XSSFSheet aba) {
        var rs = new ArrayList<Row>();
        for (int i = 9; aba.getRow(i) != null && aba.getRow(i).getCell(0).getCellType() == CellType.NUMERIC; i++) rs.add(aba.getRow(i));
        return rs;
    }

    private static List<String> textos(Row r, int n) {
        var t = new ArrayList<String>();
        for (int i = 0; i < n; i++) t.add(r.getCell(i).getStringCellValue());
        return t;
    }

    private static String textoDaAba(XSSFSheet aba) {
        var sb = new StringBuilder();
        aba.forEach(r -> r.forEach(c -> { if (c.getCellType() == CellType.STRING) sb.append(c.getStringCellValue()).append('\n'); }));
        return sb.toString();
    }

    private static List<Object[]> linhas(Object[]... rs) {
        return new ArrayList<>(List.of(rs));
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
