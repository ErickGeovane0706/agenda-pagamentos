package com.agenda.domain.venda;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.domain.loja.LojaRepository;
import com.agenda.domain.produto.Produto;
import com.agenda.domain.produto.ProdutoRepository;
import com.agenda.shared.TenantContext;
import com.agenda.shared.UserContext;
import com.agenda.shared.exception.AccessDeniedException;
import com.agenda.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service de Venda.
 * <p>
 * <b>{@code @Transactional} aqui é obrigatório, e é o oposto do que
 * {@code AssinaturaService.assinar} faz.</b> Lá a ausência de transação é
 * deliberada: aquele método faz chamadas HTTP ao Asaas de até 20s, e uma
 * transação aberta em volta seguraria conexão do pool durante toda a espera.
 * <p>
 * A venda é o caso inverso: não há <b>nenhuma</b> chamada de rede, só banco em
 * milissegundos, e baixar o estoque de N itens e gravar a venda tem de ser
 * atômico. Sem transação, uma falha no meio deixaria estoque baixado sem venda
 * registrada — mercadoria sumida dos números, sem nada explicando.
 * <p>
 * <b>Não copiar o padrão de {@code assinatura} para cá</b> (§7 do plano): ele
 * existe por um motivo que não se aplica, e copiá-lo sem entender criaria
 * exatamente o bug que a transação evita.
 */
@Service
@RequiredArgsConstructor
public class VendaService {

    private final VendaRepository vendaRepository;
    private final ProdutoRepository produtoRepository;
    private final LojaRepository lojaRepository;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public List<VendaDTO> listar(UUID lojaId, Pageable pageable) {
        UUID empresaId = TenantContext.getEmpresaId();
        return vendaRepository
            .findByEmpresaIdAndLojaIdOrderByVendidoEmDesc(empresaId, lojaId, pageable)
            .map(VendaDTO::from).getContent();
    }

    @Transactional(readOnly = true)
    public VendaDTO buscar(UUID id) {
        return VendaDTO.from(buscarDaEmpresa(id));
    }

    /**
     * Registra a venda: congela preço e nome de cada item, baixa o estoque de
     * forma atômica e grava os totais.
     * <p>
     * Se qualquer item não tiver estoque, {@link EstoqueInsuficienteException}
     * sobe e a transação inteira volta atrás — inclusive as baixas dos itens
     * que já tinham passado. Não existe venda meio registrada.
     */
    @Transactional
    public VendaDTO criar(CriarVendaRequest req) {
        UUID empresaId = TenantContext.getEmpresaId();
        var loja = lojaRepository.findById(req.lojaId())
            .orElseThrow(() -> new NotFoundException("Loja não encontrada"));
        if (!loja.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");

        var venda = Venda.builder()
            .empresa(loja.getEmpresa()).loja(loja)
            .clienteNome(req.clienteNome())
            .usuarioId(UserContext.getUsuarioId())
            .total(BigDecimal.ZERO).custoTotal(BigDecimal.ZERO)
            .build();

        var total = BigDecimal.ZERO;
        var custoTotal = BigDecimal.ZERO;

        for (var item : req.itens()) {
            var produto = carregarProdutoVendavel(item.produtoId(), loja.getId(), empresaId);

            // Baixa atômica. 0 linhas = não havia estoque; a transação volta atrás.
            if (produtoRepository.baixarEstoqueSeHouver(produto.getId(), item.quantidade()) == 0) {
                throw new EstoqueInsuficienteException(
                    "Estoque insuficiente de " + produto.getNome()
                        + " (disponível: " + produto.getQuantidade() + ")");
            }

            // O preço pode ser sobrescrito na venda; o CUSTO vem sempre do
            // produto, nunca do cliente HTTP.
            var precoVenda = item.precoVenda() != null ? item.precoVenda() : produto.getPrecoVenda();
            var precoCusto = produto.getPrecoCusto();

            venda.adicionarItem(VendaItem.builder()
                .produtoId(produto.getId())
                .produtoNome(produto.getNome())   // cópia, não ponteiro (§5.1)
                .quantidade(item.quantidade())
                .precoCusto(precoCusto)
                .precoVenda(precoVenda)
                .build());

            // Arredonda por linha e só depois soma, como nota fiscal: assim o
            // total fecha com o que o cliente vê item por item, em vez de
            // divergir por um centavo que ninguém consegue explicar.
            total = total.add(multiplicar(item.quantidade(), precoVenda));
            custoTotal = custoTotal.add(multiplicar(item.quantidade(), precoCusto));
        }

        venda.setTotal(total);
        venda.setCustoTotal(custoTotal);
        venda = vendaRepository.save(venda);

        auditoriaService.registrar("CRIAR", "VENDA", venda.getId(),
            "Total: " + total + " | Itens: " + req.itens().size()
                + (req.clienteNome() != null ? " | Cliente: " + req.clienteNome() : ""));
        return VendaDTO.from(venda);
    }

    /**
     * Cancela a venda e devolve o estoque.
     * <p>
     * O cancelamento passa pelo {@code UPDATE} condicional
     * {@link VendaRepository#cancelarSeConcluida(UUID)}: se a venda já estava
     * cancelada, nada muda e o estoque <b>não</b> é devolvido de novo. Sem
     * isso, dois cliques no botão criariam mercadoria do nada.
     * <p>
     * A venda original permanece na tabela. Cancelamento deixa rastro; edição
     * apagaria (§5.3).
     */
    @Transactional
    public void cancelar(UUID id) {
        var venda = buscarDaEmpresa(id);

        // Materializa os itens ANTES do UPDATE condicional. O
        // `clearAutomatically = true` daquele @Modifying limpa o contexto de
        // persistência e desanexa a venda: a coleção lazy, se fosse percorrida
        // depois, estouraria LazyInitializationException. Os valores já
        // carregados continuam legíveis na entidade desanexada.
        var itens = List.copyOf(venda.getItens());
        var total = venda.getTotal();

        if (vendaRepository.cancelarSeConcluida(id) == 0) {
            throw new IllegalArgumentException("Esta venda já está cancelada");
        }

        for (var item : itens) {
            produtoRepository.devolverEstoque(item.getProdutoId(), item.getQuantidade());
        }

        auditoriaService.registrar("CANCELAR", "VENDA", id,
            "Total devolvido: " + total + " | Itens: " + itens.size());
    }

    /**
     * Relatório do período: receita, custo, lucro, margem e quebra por produto.
     * <p>
     * As somas vêm do Postgres (§8 do plano). Aqui só se faz a subtração, a
     * divisão da margem e a montagem do DTO.
     *
     * @param de  primeiro dia do período, inclusive
     * @param ate último dia do período, <b>inclusive</b> — a conversão para
     *            intervalo meio-aberto acontece aqui dentro
     */
    @Transactional(readOnly = true)
    public RelatorioVendasDTO relatorio(UUID lojaId, LocalDate de, LocalDate ate) {
        UUID empresaId = TenantContext.getEmpresaId();

        // `vendidoEm` é timestamp e o filtro da tela é por dia. O intervalo é
        // meio-aberto — [de 00:00, ate+1 00:00) — porque um BETWEEN com
        // 23:59:59 perderia a venda registrada no último segundo do dia.
        var inicio = de.atStartOfDay();
        var fim = ate.plusDays(1).atStartOfDay();

        var totais = vendaRepository.totaisDoPeriodo(empresaId, lojaId, inicio, fim).getFirst();
        var receita = duasCasas((BigDecimal) totais[0]);
        var custo = duasCasas((BigDecimal) totais[1]);
        var quantidade = (long) totais[2];
        var lucro = receita.subtract(custo);

        var porProduto = vendaRepository.quebraPorProduto(empresaId, lojaId, inicio, fim)
            .stream()
            .map(linha -> {
                var receitaProduto = duasCasas((BigDecimal) linha[3]);
                var custoProduto = duasCasas((BigDecimal) linha[4]);
                return new RelatorioVendasDTO.LinhaProduto(
                    (UUID) linha[0],
                    (String) linha[1],
                    (BigDecimal) linha[2],
                    receitaProduto,
                    custoProduto,
                    receitaProduto.subtract(custoProduto));
            })
            .toList();

        return new RelatorioVendasDTO(receita, custo, lucro,
            margem(lucro, receita), quantidade, porProduto);
    }

    /**
     * Margem em pontos percentuais, ou <b>nulo</b> se não houve receita.
     * <p>
     * Dois cuidados que o §8 exige, e que são fáceis de esquecer:
     * <ul>
     *   <li><b>Receita zero devolve {@code null}</b>, não zero. A tela mostra
     *       "—". Zero por cento é um negócio que vendeu sem lucro; não vender
     *       nada é outra coisa.</li>
     *   <li><b>{@code RoundingMode} explícito.</b> Sem ele,
     *       {@code BigDecimal.divide} de uma dízima (lucro 1 sobre receita 3)
     *       <b>lança {@code ArithmeticException}</b> e derruba o relatório
     *       inteiro.</li>
     * </ul>
     */
    private BigDecimal margem(BigDecimal lucro, BigDecimal receita) {
        if (receita.signum() == 0) return null;
        return lucro.multiply(BigDecimal.valueOf(100))
            .divide(receita, 2, RoundingMode.HALF_UP);
    }

    /** Normaliza a escala vinda do SUM, que pode chegar sem as duas casas. */
    private BigDecimal duasCasas(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP);
    }

    /** Multiplica quantidade por preço e fecha em 2 casas, {@code HALF_UP}. */
    private BigDecimal multiplicar(BigDecimal quantidade, BigDecimal preco) {
        return quantidade.multiply(preco).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Carrega o produto conferindo que ele é da empresa do tenant, <b>da loja
     * da venda</b> e que está ativo.
     * <p>
     * A conferência de loja não é redundante com a de empresa: o cliente que
     * pediu o módulo tem ovos numa loja e gelo na outra, e vender ovo pelo
     * caixa do gelo poluiria o relatório de lucro das duas.
     */
    private Produto carregarProdutoVendavel(UUID produtoId, UUID lojaId, UUID empresaId) {
        var produto = produtoRepository.findById(produtoId)
            .orElseThrow(() -> new NotFoundException("Produto não encontrado"));
        if (!produto.getEmpresa().getId().equals(empresaId)) {
            throw new AccessDeniedException("Acesso negado");
        }
        if (!produto.getLoja().getId().equals(lojaId)) {
            throw new IllegalArgumentException(
                "O produto " + produto.getNome() + " não é desta loja");
        }
        if (!produto.getAtivo()) {
            throw new IllegalArgumentException(
                "O produto " + produto.getNome() + " está desativado");
        }
        return produto;
    }

    /** Carrega a venda garantindo que ela é da empresa do tenant logado. */
    private Venda buscarDaEmpresa(UUID id) {
        UUID empresaId = TenantContext.getEmpresaId();
        var venda = vendaRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Venda não encontrada"));
        if (!venda.getEmpresa().getId().equals(empresaId)) {
            throw new AccessDeniedException("Acesso negado");
        }
        return venda;
    }
}
