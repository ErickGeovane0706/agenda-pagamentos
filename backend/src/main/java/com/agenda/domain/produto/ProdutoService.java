package com.agenda.domain.produto;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.domain.loja.LojaRepository;
import com.agenda.shared.TenantContext;
import com.agenda.shared.exception.AccessDeniedException;
import com.agenda.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service de Produto. Cada operação confere o vínculo com a empresa do tenant
 * logado, igual a {@link com.agenda.domain.loja.LojaService} e
 * {@link com.agenda.domain.cheque.ChequeService} — empresa A nunca enxerga
 * produto de B.
 * <p>
 * Não existe excluir: {@link #desativar(UUID)} marca {@code ativo = false}.
 * Produto com venda registrada não pode sumir, senão o relatório perde a
 * referência e a FK de {@code venda_itens} estoura (§5.4 do plano).
 */
@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final LojaRepository lojaRepository;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public List<ProdutoDTO> listar(UUID lojaId) {
        UUID empresaId = TenantContext.getEmpresaId();
        return produtoRepository.findByEmpresaIdAndLojaIdAndAtivoTrueOrderByNome(empresaId, lojaId)
            .stream().map(ProdutoDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public ProdutoDTO buscar(UUID id) {
        return ProdutoDTO.from(buscarDaEmpresa(id));
    }

    @Transactional
    public ProdutoDTO criar(CriarProdutoRequest req) {
        UUID empresaId = TenantContext.getEmpresaId();
        var loja = lojaRepository.findById(req.lojaId())
            .orElseThrow(() -> new NotFoundException("Loja não encontrada"));
        if (!loja.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");

        if (produtoRepository.existsByLojaIdAndNomeIgnoreCaseAndAtivoTrue(req.lojaId(), req.nome())) {
            throw new IllegalArgumentException("Já existe um produto com esse nome nesta loja");
        }

        var produto = Produto.builder()
            .empresa(loja.getEmpresa()).loja(loja)
            .nome(req.nome()).quantidade(req.quantidade())
            .precoCusto(req.precoCusto()).precoVenda(req.precoVenda())
            .build();
        produto = produtoRepository.save(produto);
        auditoriaService.registrar("CRIAR", "PRODUTO", produto.getId(), "Nome: " + req.nome());
        return ProdutoDTO.from(produto);
    }

    @Transactional
    public ProdutoDTO editar(UUID id, EditarProdutoRequest req) {
        var produto = buscarDaEmpresa(id);

        if (produtoRepository.existsByLojaIdAndNomeIgnoreCaseAndAtivoTrueAndIdNot(
                produto.getLoja().getId(), req.nome(), id)) {
            throw new IllegalArgumentException("Já existe um produto com esse nome nesta loja");
        }

        // A quantidade anterior entra na auditoria: editar estoque é o caminho de
        // ajuste quando o físico diverge do sistema (§11), e ajuste sem rastro é
        // mercadoria aparecendo do nada nos números.
        var quantidadeAnterior = produto.getQuantidade();
        produto.setNome(req.nome());
        produto.setQuantidade(req.quantidade());
        produto.setPrecoCusto(req.precoCusto());
        produto.setPrecoVenda(req.precoVenda());
        produto = produtoRepository.save(produto);
        auditoriaService.registrar("EDITAR", "PRODUTO", produto.getId(),
            "Nome: " + req.nome() + " | Quantidade: " + quantidadeAnterior + " -> " + req.quantidade());
        return ProdutoDTO.from(produto);
    }

    @Transactional
    public void desativar(UUID id) {
        var produto = buscarDaEmpresa(id);
        produto.setAtivo(false);
        produtoRepository.save(produto);
        auditoriaService.registrar("DESATIVAR", "PRODUTO", id, "Nome: " + produto.getNome());
    }

    /** Carrega o produto garantindo que ele é da empresa do tenant logado. */
    private Produto buscarDaEmpresa(UUID id) {
        UUID empresaId = TenantContext.getEmpresaId();
        var produto = produtoRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Produto não encontrado"));
        if (!produto.getEmpresa().getId().equals(empresaId)) {
            throw new AccessDeniedException("Acesso negado");
        }
        return produto;
    }
}
