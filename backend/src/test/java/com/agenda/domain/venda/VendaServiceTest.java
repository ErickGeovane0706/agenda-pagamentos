package com.agenda.domain.venda;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.loja.Loja;
import com.agenda.domain.loja.LojaRepository;
import com.agenda.domain.produto.Produto;
import com.agenda.domain.produto.ProdutoRepository;
import com.agenda.shared.TenantContext;
import com.agenda.shared.exception.AccessDeniedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * A verificação que o plano chama de mais importante da Fase 3: venda sem
 * estoque não grava nada, a baixa é da quantidade exata, cancelar devolve o
 * estoque, cancelar duas vezes devolve uma vez só, e produto de outra loja é
 * recusado.
 */
@ExtendWith(MockitoExtension.class)
class VendaServiceTest {

    @Mock private VendaRepository vendaRepository;
    @Mock private ProdutoRepository produtoRepository;
    @Mock private LojaRepository lojaRepository;
    @Mock private AuditoriaService auditoriaService;

    private VendaService vendaService;
    private Empresa empresa;
    private Loja loja;
    private Produto gelo;

    @BeforeEach
    void setUp() {
        vendaService = new VendaService(vendaRepository, produtoRepository, lojaRepository, auditoriaService);
        empresa = Empresa.builder().id(UUID.randomUUID()).nome("Gelo e Ovos").build();
        loja = Loja.builder().id(UUID.randomUUID()).empresa(empresa).nome("Loja do Gelo").build();
        gelo = Produto.builder()
            .id(UUID.randomUUID()).empresa(empresa).loja(loja).nome("Gelo 5kg")
            .quantidade(new BigDecimal("40.000"))
            .precoCusto(new BigDecimal("3.00")).precoVenda(new BigDecimal("8.00"))
            .ativo(true).build();
        TenantContext.setEmpresaId(empresa.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CriarVendaRequest pedido(UUID produtoId, String qtd, String precoVenda) {
        return new CriarVendaRequest(loja.getId(), "Maria",
            List.of(new CriarVendaRequest.Item(produtoId, new BigDecimal(qtd),
                precoVenda == null ? null : new BigDecimal(precoVenda))));
    }

    private void lojaEncontrada() {
        when(lojaRepository.findById(loja.getId())).thenReturn(Optional.of(loja));
    }

    private void vendaSalvaComoVeio() {
        when(vendaRepository.save(any())).thenAnswer(i -> {
            var v = i.getArgument(0, Venda.class);
            v.setId(UUID.randomUUID());
            return v;
        });
    }

    @Test
    void criar_DeveBaixarAQuantidadeExataEGravarOsTotais() {
        lojaEncontrada();
        when(produtoRepository.findById(gelo.getId())).thenReturn(Optional.of(gelo));
        when(produtoRepository.baixarEstoqueSeHouver(eq(gelo.getId()), any())).thenReturn(1);
        vendaSalvaComoVeio();

        var result = vendaService.criar(pedido(gelo.getId(), "3.000", null));

        // 3 x 8,00 = 24,00 de receita; 3 x 3,00 = 9,00 de custo; lucro 15,00.
        assertEquals(new BigDecimal("24.00"), result.total());
        assertEquals(new BigDecimal("9.00"), result.custoTotal());
        assertEquals(new BigDecimal("15.00"), result.lucro());
        assertEquals(StatusVenda.CONCLUIDA, result.status());
        // A baixa foi da quantidade exata do pedido, nem mais nem menos.
        verify(produtoRepository).baixarEstoqueSeHouver(gelo.getId(), new BigDecimal("3.000"));
    }

    /**
     * §5.1, a decisão mais importante do módulo: o item guarda CÓPIA do nome e
     * dos preços. É o que impede que um reajuste no cadastro reescreva o lucro
     * do passado.
     */
    @Test
    void criar_DeveCongelarNomeEPrecosNoItem() {
        lojaEncontrada();
        when(produtoRepository.findById(gelo.getId())).thenReturn(Optional.of(gelo));
        when(produtoRepository.baixarEstoqueSeHouver(eq(gelo.getId()), any())).thenReturn(1);
        vendaSalvaComoVeio();

        var result = vendaService.criar(pedido(gelo.getId(), "2.000", null));

        var item = result.itens().getFirst();
        assertEquals("Gelo 5kg", item.produtoNome());
        assertEquals(new BigDecimal("3.00"), item.precoCusto());
        assertEquals(new BigDecimal("8.00"), item.precoVenda());
    }

    /** Preço editável na venda (decidido em 27/08): sobrescreve o do cadastro. */
    @Test
    void criar_DeveUsarOPrecoInformadoQuandoVemNoPedido() {
        lojaEncontrada();
        when(produtoRepository.findById(gelo.getId())).thenReturn(Optional.of(gelo));
        when(produtoRepository.baixarEstoqueSeHouver(eq(gelo.getId()), any())).thenReturn(1);
        vendaSalvaComoVeio();

        var result = vendaService.criar(pedido(gelo.getId(), "2.000", "7.50"));

        assertEquals(new BigDecimal("15.00"), result.total());      // 2 x 7,50
        assertEquals(new BigDecimal("6.00"), result.custoTotal());  // custo segue o do produto
        assertEquals(new BigDecimal("7.50"), result.itens().getFirst().precoVenda());
    }

    /** O custo nunca vem do cliente HTTP, mesmo com preço sobrescrito. */
    @Test
    void criar_NaoDeveAceitarCustoDeFora() {
        lojaEncontrada();
        when(produtoRepository.findById(gelo.getId())).thenReturn(Optional.of(gelo));
        when(produtoRepository.baixarEstoqueSeHouver(eq(gelo.getId()), any())).thenReturn(1);
        vendaSalvaComoVeio();

        var result = vendaService.criar(pedido(gelo.getId(), "1.000", "99.00"));

        assertEquals(new BigDecimal("3.00"), result.itens().getFirst().precoCusto());
    }

    /**
     * §5.2 — a verificação central: sem estoque, 409 e <b>nenhuma venda
     * gravada</b>. O rollback é da transação; aqui se prova que o {@code save}
     * nunca é chamado.
     */
    @Test
    void criar_SemEstoque_DeveLancar409ENaoGravarVenda() {
        lojaEncontrada();
        when(produtoRepository.findById(gelo.getId())).thenReturn(Optional.of(gelo));
        // 0 linhas afetadas = não havia estoque.
        when(produtoRepository.baixarEstoqueSeHouver(eq(gelo.getId()), any())).thenReturn(0);

        var ex = assertThrows(EstoqueInsuficienteException.class,
            () -> vendaService.criar(pedido(gelo.getId(), "999.000", null)));

        assertTrue(ex.getMessage().contains("Gelo 5kg"));
        verify(vendaRepository, never()).save(any());
        verify(auditoriaService, never()).registrar(any(), any(), any(), any());
    }

    /**
     * Venda de vários itens em que o segundo falta: o primeiro já foi baixado,
     * e é a transação que desfaz. O que se prova aqui é que a venda não é
     * gravada pela metade.
     */
    @Test
    void criar_ComUmItemSemEstoque_NaoDeveGravarVendaParcial() {
        var ovo = Produto.builder()
            .id(UUID.randomUUID()).empresa(empresa).loja(loja).nome("Ovo branco")
            .quantidade(new BigDecimal("0.000"))
            .precoCusto(new BigDecimal("10.00")).precoVenda(new BigDecimal("15.00"))
            .ativo(true).build();
        lojaEncontrada();
        when(produtoRepository.findById(gelo.getId())).thenReturn(Optional.of(gelo));
        when(produtoRepository.findById(ovo.getId())).thenReturn(Optional.of(ovo));
        when(produtoRepository.baixarEstoqueSeHouver(eq(gelo.getId()), any())).thenReturn(1);
        when(produtoRepository.baixarEstoqueSeHouver(eq(ovo.getId()), any())).thenReturn(0);

        var req = new CriarVendaRequest(loja.getId(), "Maria", List.of(
            new CriarVendaRequest.Item(gelo.getId(), new BigDecimal("1.000"), null),
            new CriarVendaRequest.Item(ovo.getId(), new BigDecimal("1.000"), null)));

        assertThrows(EstoqueInsuficienteException.class, () -> vendaService.criar(req));
        verify(vendaRepository, never()).save(any());
    }

    /** §4.1: produto de outra loja da mesma empresa é recusado. */
    @Test
    void criar_DeveRecusarProdutoDeOutraLoja() {
        var outraLoja = Loja.builder().id(UUID.randomUUID()).empresa(empresa).nome("Loja dos Ovos").build();
        var ovo = Produto.builder()
            .id(UUID.randomUUID()).empresa(empresa).loja(outraLoja).nome("Ovo branco")
            .quantidade(new BigDecimal("30.000"))
            .precoCusto(new BigDecimal("10.00")).precoVenda(new BigDecimal("15.00"))
            .ativo(true).build();
        lojaEncontrada();
        when(produtoRepository.findById(ovo.getId())).thenReturn(Optional.of(ovo));

        var ex = assertThrows(IllegalArgumentException.class,
            () -> vendaService.criar(pedido(ovo.getId(), "1.000", null)));

        assertTrue(ex.getMessage().contains("não é desta loja"));
        verify(produtoRepository, never()).baixarEstoqueSeHouver(any(), any());
        verify(vendaRepository, never()).save(any());
    }

    @Test
    void criar_DeveRecusarProdutoDeOutraEmpresa() {
        var outraEmpresa = Empresa.builder().id(UUID.randomUUID()).build();
        var alheio = Produto.builder()
            .id(UUID.randomUUID()).empresa(outraEmpresa)
            .loja(Loja.builder().id(UUID.randomUUID()).empresa(outraEmpresa).build())
            .nome("Alheio").quantidade(BigDecimal.TEN)
            .precoCusto(BigDecimal.ONE).precoVenda(BigDecimal.TEN).ativo(true).build();
        lojaEncontrada();
        when(produtoRepository.findById(alheio.getId())).thenReturn(Optional.of(alheio));

        assertThrows(AccessDeniedException.class,
            () -> vendaService.criar(pedido(alheio.getId(), "1.000", null)));
        verify(produtoRepository, never()).baixarEstoqueSeHouver(any(), any());
    }

    @Test
    void criar_DeveRecusarProdutoDesativado() {
        gelo.setAtivo(false);
        lojaEncontrada();
        when(produtoRepository.findById(gelo.getId())).thenReturn(Optional.of(gelo));

        var ex = assertThrows(IllegalArgumentException.class,
            () -> vendaService.criar(pedido(gelo.getId(), "1.000", null)));

        assertTrue(ex.getMessage().contains("desativado"));
        verify(produtoRepository, never()).baixarEstoqueSeHouver(any(), any());
    }

    @Test
    void criar_DeveRecusarLojaDeOutraEmpresa() {
        var lojaAlheia = Loja.builder().id(UUID.randomUUID())
            .empresa(Empresa.builder().id(UUID.randomUUID()).build()).build();
        when(lojaRepository.findById(lojaAlheia.getId())).thenReturn(Optional.of(lojaAlheia));

        var req = new CriarVendaRequest(lojaAlheia.getId(), null,
            List.of(new CriarVendaRequest.Item(gelo.getId(), BigDecimal.ONE, null)));

        assertThrows(AccessDeniedException.class, () -> vendaService.criar(req));
        verify(vendaRepository, never()).save(any());
    }

    // ───────────────────────── cancelamento ─────────────────────────

    private Venda vendaConcluida() {
        var venda = Venda.builder()
            .id(UUID.randomUUID()).empresa(empresa).loja(loja).clienteNome("Maria")
            .total(new BigDecimal("24.00")).custoTotal(new BigDecimal("9.00"))
            .status(StatusVenda.CONCLUIDA).build();
        venda.adicionarItem(VendaItem.builder()
            .id(UUID.randomUUID()).produtoId(gelo.getId()).produtoNome("Gelo 5kg")
            .quantidade(new BigDecimal("3.000"))
            .precoCusto(new BigDecimal("3.00")).precoVenda(new BigDecimal("8.00"))
            .build());
        return venda;
    }

    /** §5.3: cancelar devolve o estoque. */
    @Test
    void cancelar_DeveDevolverOEstoque() {
        var venda = vendaConcluida();
        when(vendaRepository.findById(venda.getId())).thenReturn(Optional.of(venda));
        when(vendaRepository.cancelarSeConcluida(venda.getId())).thenReturn(1);

        vendaService.cancelar(venda.getId());

        verify(produtoRepository).devolverEstoque(gelo.getId(), new BigDecimal("3.000"));
        verify(auditoriaService).registrar(eq("CANCELAR"), eq("VENDA"), eq(venda.getId()), anyString());
    }

    /**
     * §5.3, a parte que mais importa: <b>cancelar duas vezes devolve uma vez
     * só</b>. O segundo cancelamento encontra 0 linhas afetadas no UPDATE
     * condicional e não devolve estoque — sem isso, dois cliques no botão
     * criariam mercadoria do nada.
     */
    @Test
    void cancelar_DuasVezes_DeveDevolverOEstoqueUmaVezSo() {
        var venda = vendaConcluida();
        when(vendaRepository.findById(venda.getId())).thenReturn(Optional.of(venda));
        // Primeira chamada cancela (1 linha); a segunda não acha nada (0 linhas).
        when(vendaRepository.cancelarSeConcluida(venda.getId())).thenReturn(1, 0);

        vendaService.cancelar(venda.getId());
        assertThrows(IllegalArgumentException.class, () -> vendaService.cancelar(venda.getId()));

        verify(produtoRepository, times(1)).devolverEstoque(gelo.getId(), new BigDecimal("3.000"));
    }

    /** A venda original permanece: cancelamento deixa rastro, não apaga. */
    @Test
    void cancelar_NaoDeveApagarAVenda() {
        var venda = vendaConcluida();
        when(vendaRepository.findById(venda.getId())).thenReturn(Optional.of(venda));
        when(vendaRepository.cancelarSeConcluida(venda.getId())).thenReturn(1);

        vendaService.cancelar(venda.getId());

        verify(vendaRepository, never()).delete(any());
        verify(vendaRepository, never()).deleteById(any());
    }

    @Test
    void cancelar_DeveNegarVendaDeOutraEmpresa() {
        var venda = vendaConcluida();
        venda.setEmpresa(Empresa.builder().id(UUID.randomUUID()).build());
        when(vendaRepository.findById(venda.getId())).thenReturn(Optional.of(venda));

        assertThrows(AccessDeniedException.class, () -> vendaService.cancelar(venda.getId()));
        verify(vendaRepository, never()).cancelarSeConcluida(any());
        verify(produtoRepository, never()).devolverEstoque(any(), any());
    }
}
