package com.agenda.domain.produto;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.loja.Loja;
import com.agenda.domain.loja.LojaRepository;
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
 * As quatro verificações do módulo de produtos: isolamento
 * entre empresas, nome duplicado recusado, desativar-e-recriar liberado (a regra
 * do índice parcial) e produto que não se apaga.
 * <p>
 * Preço negativo é recusado por Bean Validation no
 * {@link CriarProdutoRequest}, não aqui — o service nunca é chamado.
 */
@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @Mock private ProdutoRepository produtoRepository;
    @Mock private LojaRepository lojaRepository;
    @Mock private AuditoriaService auditoriaService;

    private ProdutoService produtoService;
    private Empresa empresa;
    private Loja loja;

    @BeforeEach
    void setUp() {
        produtoService = new ProdutoService(produtoRepository, lojaRepository, auditoriaService);
        empresa = Empresa.builder().id(UUID.randomUUID()).nome("Empresa Teste").build();
        loja = Loja.builder().id(UUID.randomUUID()).empresa(empresa).nome("Loja do Gelo").build();
        TenantContext.setEmpresaId(empresa.getId());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Produto produtoSalvo(String nome, BigDecimal quantidade) {
        return Produto.builder()
            .id(UUID.randomUUID()).empresa(empresa).loja(loja).nome(nome)
            .quantidade(quantidade)
            .precoCusto(new BigDecimal("10.00")).precoVenda(new BigDecimal("15.00"))
            .build();
    }

    private Produto produtoDeOutraEmpresa() {
        var outraEmpresa = Empresa.builder().id(UUID.randomUUID()).nome("Outra").build();
        var outraLoja = Loja.builder().id(UUID.randomUUID()).empresa(outraEmpresa).build();
        return Produto.builder()
            .id(UUID.randomUUID()).empresa(outraEmpresa).loja(outraLoja).nome("Ovo branco")
            .quantidade(BigDecimal.TEN)
            .precoCusto(new BigDecimal("10.00")).precoVenda(new BigDecimal("15.00"))
            .build();
    }

    @Test
    void criar_DeveSalvarProdutoNaLoja() {
        var req = new CriarProdutoRequest(loja.getId(), "Gelo 5kg",
            new BigDecimal("40.000"), new BigDecimal("3.00"), new BigDecimal("8.00"));
        when(lojaRepository.findById(loja.getId())).thenReturn(Optional.of(loja));
        when(produtoRepository.existsByLojaIdAndNomeIgnoreCaseAndAtivoTrue(loja.getId(), "Gelo 5kg"))
            .thenReturn(false);
        when(produtoRepository.save(any())).thenAnswer(i -> {
            var p = i.getArgument(0, Produto.class);
            p.setId(UUID.randomUUID());
            return p;
        });

        var result = produtoService.criar(req);

        assertEquals("Gelo 5kg", result.nome());
        assertEquals(loja.getId(), result.lojaId());
        assertTrue(result.ativo());
        verify(auditoriaService).registrar(eq("CRIAR"), eq("PRODUTO"), any(), anyString());
    }

    /** Verificação 1 do plano: empresa A não enxerga produto de B. */
    @Test
    void buscar_DeveNegarProdutoDeOutraEmpresa() {
        var produtoAlheio = produtoDeOutraEmpresa();
        when(produtoRepository.findById(produtoAlheio.getId())).thenReturn(Optional.of(produtoAlheio));

        assertThrows(AccessDeniedException.class, () -> produtoService.buscar(produtoAlheio.getId()));
    }

    /** Verificação 1, outro lado: a listagem é sempre filtrada pela empresa do tenant. */
    @Test
    void listar_DeveFiltrarPelaEmpresaDoTenantEPelaLoja() {
        when(produtoRepository.findByEmpresaIdAndLojaIdAndAtivoTrueOrderByNome(empresa.getId(), loja.getId()))
            .thenReturn(List.of(produtoSalvo("Gelo 5kg", new BigDecimal("40.000"))));

        var result = produtoService.listar(loja.getId());

        assertEquals(1, result.size());
        verify(produtoRepository)
            .findByEmpresaIdAndLojaIdAndAtivoTrueOrderByNome(empresa.getId(), loja.getId());
    }

    @Test
    void criar_DeveNegarLojaDeOutraEmpresa() {
        var outraEmpresa = Empresa.builder().id(UUID.randomUUID()).build();
        var lojaAlheia = Loja.builder().id(UUID.randomUUID()).empresa(outraEmpresa).build();
        var req = new CriarProdutoRequest(lojaAlheia.getId(), "Gelo 5kg",
            BigDecimal.ZERO, new BigDecimal("3.00"), new BigDecimal("8.00"));
        when(lojaRepository.findById(lojaAlheia.getId())).thenReturn(Optional.of(lojaAlheia));

        assertThrows(AccessDeniedException.class, () -> produtoService.criar(req));
        verify(produtoRepository, never()).save(any());
    }

    /** Verificação 2 do plano: nome duplicado na mesma loja é recusado. */
    @Test
    void criar_DeveRecusarNomeDuplicadoNaMesmaLoja() {
        var req = new CriarProdutoRequest(loja.getId(), "Gelo 5kg",
            BigDecimal.ZERO, new BigDecimal("3.00"), new BigDecimal("8.00"));
        when(lojaRepository.findById(loja.getId())).thenReturn(Optional.of(loja));
        when(produtoRepository.existsByLojaIdAndNomeIgnoreCaseAndAtivoTrue(loja.getId(), "Gelo 5kg"))
            .thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> produtoService.criar(req));
        verify(produtoRepository, never()).save(any());
    }

    /**
     * Verificação 3 do plano, a que prova a regra do índice parcial: o service
     * consulta a duplicidade <b>só entre os ativos</b>. É isso que faz
     * "desativar Ovo branco e cadastrar Ovo branco de novo" funcionar, em vez de
     * dar um "nome já existe" apontando para um produto que ninguém vê em tela.
     */
    @Test
    void criar_DevePermitirNomeDeProdutoDesativado() {
        var req = new CriarProdutoRequest(loja.getId(), "Ovo branco",
            new BigDecimal("30.000"), new BigDecimal("10.00"), new BigDecimal("15.00"));
        when(lojaRepository.findById(loja.getId())).thenReturn(Optional.of(loja));
        // O homônimo desativado existe no banco, mas a consulta que o service faz
        // ignora inativos — então ela devolve false e a criação passa.
        when(produtoRepository.existsByLojaIdAndNomeIgnoreCaseAndAtivoTrue(loja.getId(), "Ovo branco"))
            .thenReturn(false);
        when(produtoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = produtoService.criar(req);

        assertEquals("Ovo branco", result.nome());
        // A asserção que importa: a checagem foi a filtrada por ativo, e não uma
        // que varre o histórico inteiro.
        verify(produtoRepository).existsByLojaIdAndNomeIgnoreCaseAndAtivoTrue(loja.getId(), "Ovo branco");
    }

    @Test
    void editar_DeveAtualizarEAuditarAMudancaDeQuantidade() {
        var produto = produtoSalvo("Gelo 5kg", new BigDecimal("40.000"));
        var req = new EditarProdutoRequest("Gelo 5kg", new BigDecimal("37.000"),
            new BigDecimal("3.50"), new BigDecimal("9.00"));
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));
        when(produtoRepository.existsByLojaIdAndNomeIgnoreCaseAndAtivoTrueAndIdNot(
            loja.getId(), "Gelo 5kg", produto.getId())).thenReturn(false);
        when(produtoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = produtoService.editar(produto.getId(), req);

        assertEquals(new BigDecimal("37.000"), result.quantidade());
        assertEquals(new BigDecimal("9.00"), result.precoVenda());
        // O ajuste de estoque tem de deixar rastro do antes e do depois (§11).
        verify(auditoriaService).registrar(eq("EDITAR"), eq("PRODUTO"), eq(produto.getId()),
            contains("40.000 -> 37.000"));
    }

    /** O produto não colide consigo mesmo ao ser editado sem trocar de nome. */
    @Test
    void editar_NaoDeveColidirComOProprioNome() {
        var produto = produtoSalvo("Gelo 5kg", new BigDecimal("40.000"));
        var req = new EditarProdutoRequest("Gelo 5kg", new BigDecimal("40.000"),
            new BigDecimal("3.00"), new BigDecimal("8.00"));
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));
        when(produtoRepository.existsByLojaIdAndNomeIgnoreCaseAndAtivoTrueAndIdNot(
            loja.getId(), "Gelo 5kg", produto.getId())).thenReturn(false);
        when(produtoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals("Gelo 5kg", produtoService.editar(produto.getId(), req).nome());
    }

    /** §5.4: não existe apagar. Desativar preserva a linha para o histórico de vendas. */
    @Test
    void desativar_DeveMarcarInativoSemApagar() {
        var produto = produtoSalvo("Gelo 5kg", new BigDecimal("40.000"));
        when(produtoRepository.findById(produto.getId())).thenReturn(Optional.of(produto));

        produtoService.desativar(produto.getId());

        assertFalse(produto.getAtivo());
        verify(produtoRepository).save(produto);
        verify(produtoRepository, never()).delete(any());
        verify(produtoRepository, never()).deleteById(any());
        verify(auditoriaService).registrar(eq("DESATIVAR"), eq("PRODUTO"), eq(produto.getId()), anyString());
    }

    @Test
    void desativar_DeveNegarProdutoDeOutraEmpresa() {
        var produtoAlheio = produtoDeOutraEmpresa();
        when(produtoRepository.findById(produtoAlheio.getId())).thenReturn(Optional.of(produtoAlheio));

        assertThrows(AccessDeniedException.class, () -> produtoService.desativar(produtoAlheio.getId()));
        verify(produtoRepository, never()).save(any());
    }
}
