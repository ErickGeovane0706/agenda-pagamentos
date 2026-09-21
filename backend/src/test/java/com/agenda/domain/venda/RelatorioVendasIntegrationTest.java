package com.agenda.domain.venda;

import com.agenda.domain.assinatura.Assinatura;
import com.agenda.domain.assinatura.AssinaturaRepository;
import com.agenda.domain.assinatura.StatusAssinatura;
import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.empresa.EmpresaRepository;
import com.agenda.domain.loja.Loja;
import com.agenda.domain.loja.LojaRepository;
import com.agenda.domain.produto.CriarProdutoRequest;
import com.agenda.domain.produto.EditarProdutoRequest;
import com.agenda.domain.produto.ProdutoRepository;
import com.agenda.domain.produto.ProdutoService;
import com.agenda.shared.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <b>O teste que justifica o módulo inteiro</b>
 * ({@code docs/adr/0007-venda-itens-congela-preco.md}), e o único jeito
 * honesto de escrevê-lo é contra um Postgres de verdade.
 * <p>
 * Com repositório mockado ele não provaria nada: o que está sob teste não é a
 * aritmética em Java, é <b>de qual tabela a query de agregação lê o preço</b>.
 * Se ela fizer {@code JOIN produtos}, o lucro do passado se reescreve a cada
 * reajuste de cadastro — sem erro, sem aviso, sem nada nos logs.
 * <p>
 * O roteiro é o do plano: vende com custo 10 e preço 15, muda o custo do
 * cadastro para 12, e o lucro tem de continuar 5. Se der 3, o §5.1 foi
 * violado.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class RelatorioVendasIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired private ProdutoService produtoService;
    @Autowired private VendaService vendaService;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private LojaRepository lojaRepository;
    @Autowired private AssinaturaRepository assinaturaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private VendaRepository vendaRepository;

    @Autowired private JdbcTemplate jdbcTemplate;

    private Loja loja;

    @BeforeEach
    void setUp() {
        var empresa = empresaRepository.save(
            Empresa.builder().nome("Gelo e Ovos " + UUID.randomUUID()).pdvHabilitado(true).build());
        // A loja é criada pelo repositório, e não pelo LojaService, para não
        // depender do gate de assinatura neste teste — o que está sob prova aqui
        // é a agregação do relatório.
        loja = lojaRepository.save(
            Loja.builder().empresa(empresa).nome("Loja do Ovo").cor("#b45309").build());
        assinaturaRepository.save(Assinatura.builder()
            .empresa(empresa).status(StatusAssinatura.ATIVA).lojasContratadas(1).build());

        TenantContext.setEmpresaId(empresa.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private RelatorioVendasDTO relatorioDeHoje() {
        return vendaService.relatorio(loja.getId(), LocalDate.now(), LocalDate.now());
    }

    /**
     * O roteiro exato do plano. <b>Se este teste ficar vermelho com lucro 3,00,
     * alguém passou a ler preço de {@code produtos} numa agregação.</b>
     */
    @Test
    void relatorio_NaoDeveMudarOLucroDoPassadoQuandoOCustoDoProdutoMuda() {
        // 1. produto com custo 10, venda 15
        var produto = produtoService.criar(new CriarProdutoRequest(
            loja.getId(), "Ovo branco", new BigDecimal("10.000"),
            new BigDecimal("10.00"), new BigDecimal("15.00")));

        // 2. vende uma unidade
        vendaService.criar(new CriarVendaRequest(loja.getId(), "Maria",
            List.of(new CriarVendaRequest.Item(produto.id(), BigDecimal.ONE, null))));

        var antes = relatorioDeHoje();
        assertEquals(new BigDecimal("15.00"), antes.receita());
        assertEquals(new BigDecimal("10.00"), antes.custo());
        assertEquals(new BigDecimal("5.00"), antes.lucro());

        // 3. o custo do cadastro sobe para 12 — reajuste normal de fornecedor
        produtoService.editar(produto.id(), new EditarProdutoRequest(
            "Ovo branco", new BigDecimal("9.000"),
            new BigDecimal("12.00"), new BigDecimal("15.00")));

        // 4. o lucro daquela venda tem de continuar 5,00
        var depois = relatorioDeHoje();
        assertEquals(new BigDecimal("15.00"), depois.receita(),
            "A receita da venda passada mudou com o reajuste do cadastro");
        assertEquals(new BigDecimal("10.00"), depois.custo(),
            "O CUSTO da venda passada mudou com o reajuste — o §5.1 foi violado: "
                + "alguma agregação está lendo preço de `produtos` em vez de `venda_itens`");
        assertEquals(new BigDecimal("5.00"), depois.lucro(),
            "O lucro do passado se reescreveu sozinho");
    }

    /** A mesma prova na quebra por produto, que é outra query. */
    @Test
    void quebraPorProduto_TambemDeveUsarOPrecoCongelado() {
        var produto = produtoService.criar(new CriarProdutoRequest(
            loja.getId(), "Gelo 5kg", new BigDecimal("10.000"),
            new BigDecimal("3.00"), new BigDecimal("8.00")));
        vendaService.criar(new CriarVendaRequest(loja.getId(), null,
            List.of(new CriarVendaRequest.Item(produto.id(), new BigDecimal("2"), null))));

        produtoService.editar(produto.id(), new EditarProdutoRequest(
            "Gelo 5kg", new BigDecimal("8.000"),
            new BigDecimal("7.00"), new BigDecimal("9.00")));

        var linha = relatorioDeHoje().porProduto().getFirst();
        assertEquals("Gelo 5kg", linha.produtoNome());
        assertEquals(new BigDecimal("16.00"), linha.receita());  // 2 x 8,00, não 2 x 9,00
        assertEquals(new BigDecimal("6.00"), linha.custo());     // 2 x 3,00, não 2 x 7,00
        assertEquals(new BigDecimal("10.00"), linha.lucro());
    }

    /** Venda cancelada sai de toda agregação — senão conta como lucro. */
    @Test
    void relatorio_NaoDeveContarVendaCancelada() {
        var produto = produtoService.criar(new CriarProdutoRequest(
            loja.getId(), "Gelo 2kg", new BigDecimal("10.000"),
            new BigDecimal("2.00"), new BigDecimal("5.00")));
        var venda = vendaService.criar(new CriarVendaRequest(loja.getId(), null,
            List.of(new CriarVendaRequest.Item(produto.id(), BigDecimal.ONE, null))));

        assertEquals(new BigDecimal("5.00"), relatorioDeHoje().receita());

        vendaService.cancelar(venda.id());

        var depois = relatorioDeHoje();
        assertEquals(new BigDecimal("0.00"), depois.receita());
        assertEquals(new BigDecimal("0.00"), depois.custo());
        assertTrue(depois.porProduto().isEmpty(), "Produto de venda cancelada apareceu na quebra");
        // E o estoque voltou.
        assertEquals(0, produtoRepository.findById(produto.id()).orElseThrow()
            .getQuantidade().compareTo(new BigDecimal("10.000")));
    }

    /**
     * §8: período sem venda tem margem <b>indefinida</b>, não 0%. Devolver zero
     * seria mentira — 0% de margem é um negócio que vende sem lucro, e não um
     * negócio que não vendeu.
     */
    @Test
    void relatorio_SemVenda_DeveDevolverMargemNula() {
        var vazio = relatorioDeHoje();

        assertEquals(new BigDecimal("0.00"), vazio.receita());
        assertEquals(new BigDecimal("0.00"), vazio.lucro());
        assertNull(vazio.margemPercentual(), "Margem de período sem venda tem de ser nula, não 0");
        assertEquals(0, vazio.quantidadeVendas());
    }

    /**
     * §8: dízima na margem não pode derrubar o relatório.
     * {@code BigDecimal.divide} sem arredondamento explícito lança
     * {@code ArithmeticException} — 1/3 não tem representação exata.
     */
    @Test
    void relatorio_ComMargemDeDizima_NaoDeveEstourar() {
        var produto = produtoService.criar(new CriarProdutoRequest(
            loja.getId(), "Dizima", new BigDecimal("10.000"),
            new BigDecimal("2.00"), new BigDecimal("3.00")));
        vendaService.criar(new CriarVendaRequest(loja.getId(), null,
            List.of(new CriarVendaRequest.Item(produto.id(), BigDecimal.ONE, null))));

        var r = relatorioDeHoje();

        // lucro 1,00 / receita 3,00 = 33,333...%
        assertEquals(new BigDecimal("33.33"), r.margemPercentual());
    }

    /** O relatório de uma loja não enxerga venda de outra (§4.1). */
    @Test
    void relatorio_NaoDeveMisturarLojas() {
        var outraLoja = lojaRepository.save(Loja.builder()
            .empresa(loja.getEmpresa()).nome("Loja do Gelo").cor("#0369a1").build());

        var ovo = produtoService.criar(new CriarProdutoRequest(
            loja.getId(), "Ovo", new BigDecimal("10.000"),
            new BigDecimal("10.00"), new BigDecimal("15.00")));
        var gelo = produtoService.criar(new CriarProdutoRequest(
            outraLoja.getId(), "Gelo", new BigDecimal("10.000"),
            new BigDecimal("3.00"), new BigDecimal("8.00")));

        vendaService.criar(new CriarVendaRequest(loja.getId(), null,
            List.of(new CriarVendaRequest.Item(ovo.id(), BigDecimal.ONE, null))));
        vendaService.criar(new CriarVendaRequest(outraLoja.getId(), null,
            List.of(new CriarVendaRequest.Item(gelo.id(), BigDecimal.ONE, null))));

        assertEquals(new BigDecimal("15.00"), relatorioDeHoje().receita());
        assertEquals(new BigDecimal("8.00"),
            vendaService.relatorio(outraLoja.getId(), LocalDate.now(), LocalDate.now()).receita());
    }

    /** Soma de várias vendas, com quebra ordenada pelo que dá mais dinheiro. */
    @Test
    void relatorio_DeveSomarVariasVendasEOrdenarAQuebraPorLucro() {
        var ovo = produtoService.criar(new CriarProdutoRequest(
            loja.getId(), "Ovo", new BigDecimal("100.000"),
            new BigDecimal("10.00"), new BigDecimal("15.00")));
        var gelo = produtoService.criar(new CriarProdutoRequest(
            loja.getId(), "Gelo", new BigDecimal("100.000"),
            new BigDecimal("3.00"), new BigDecimal("8.00")));

        // Ovo: 1 unidade, lucro 5. Gelo: 4 unidades, lucro 20.
        vendaService.criar(new CriarVendaRequest(loja.getId(), null,
            List.of(new CriarVendaRequest.Item(ovo.id(), BigDecimal.ONE, null))));
        vendaService.criar(new CriarVendaRequest(loja.getId(), null,
            List.of(new CriarVendaRequest.Item(gelo.id(), new BigDecimal("4"), null))));

        var r = relatorioDeHoje();

        assertEquals(new BigDecimal("47.00"), r.receita());   // 15 + 32
        assertEquals(new BigDecimal("22.00"), r.custo());     // 10 + 12
        assertEquals(new BigDecimal("25.00"), r.lucro());
        assertEquals(2, r.quantidadeVendas());
        // O que dá mais dinheiro vem primeiro — é a pergunta que a tela responde.
        assertEquals("Gelo", r.porProduto().getFirst().produtoNome());
        assertEquals(new BigDecimal("20.00"), r.porProduto().getFirst().lucro());
    }

    /** Venda de ontem não entra no relatório de hoje. */
    @Test
    void relatorio_DeveRespeitarOPeriodo() {
        var produto = produtoService.criar(new CriarProdutoRequest(
            loja.getId(), "Ovo", new BigDecimal("10.000"),
            new BigDecimal("10.00"), new BigDecimal("15.00")));
        var venda = vendaService.criar(new CriarVendaRequest(loja.getId(), null,
            List.of(new CriarVendaRequest.Item(produto.id(), BigDecimal.ONE, null))));

        // Empurra a venda para ontem com SQL direto, e não com setter + save:
        // `vendidoEm` é mapeado com `updatable = false` (a data da venda não
        // muda, o que é a regra certa), então o UPDATE gerado pela entidade
        // ignoraria o campo em silêncio e este teste passaria por engano —
        // foi o que aconteceu na primeira tentativa.
        jdbcTemplate.update("UPDATE vendas SET vendido_em = ? WHERE id = ?",
            LocalDateTime.now().minusDays(1), venda.id());

        assertEquals(new BigDecimal("0.00"), relatorioDeHoje().receita());
        assertEquals(new BigDecimal("15.00"),
            vendaService.relatorio(loja.getId(), LocalDate.now().minusDays(1), LocalDate.now()).receita());
    }
}
