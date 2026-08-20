package com.agenda.domain.pix;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.domain.arquivo.ArquivoService;
import com.agenda.domain.arquivo.TipoDocumento;
import com.agenda.domain.loja.LojaRepository;
import com.agenda.shared.TenantContext;
import com.agenda.shared.exception.AccessDeniedException;
import com.agenda.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Serviço responsável pelas operações de negócio de pagamentos PIX.
 * <p>
 * A lógica é praticamente idêntica à de {@link com.agenda.domain.boleto.BoletoService},
 * diferenciando-se apenas pelos campos {@code chavePix} e {@code tipoChave}.
 * Multi-tenancy, auditoria e validação de acesso seguem o mesmo padrão.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class PixService {

    private final PagamentoPixRepository pixRepository;
    private final LojaRepository lojaRepository;
    private final ArquivoService arquivoService;
    private final AuditoriaService auditoriaService;

    /**
     * Lista PIX com paginação e filtros dinâmicos (Specification).
     * Filtro de empresa é sempre aplicado (multi-tenant).
     */
    @Transactional(readOnly = true)
    public Page<PixDTO> listar(UUID lojaId, StatusPix status, LocalDate de, LocalDate ate, String fornecedor, Pageable pageable) {
        UUID empresaId = TenantContext.getEmpresaId();
        var spec = PagamentoPixSpecification.comFiltros(empresaId, lojaId, status, de, ate, fornecedor);
        return pixRepository.findAll(spec, pageable)
                .map(PixDTO::from);
    }

    /**
     * Índice (0-based) da primeira página com PIX pendente ou vencido, respeitando os
     * mesmos filtros e a ordenação (vencimento, fornecedor, id) da listagem. Ver
     * {@link com.agenda.domain.boleto.BoletoService#paginaPendente} para a estratégia.
     */
    @Transactional(readOnly = true)
    public int paginaPendente(UUID lojaId, StatusPix status, LocalDate de, LocalDate ate, String fornecedor, int size) {
        if (size <= 0) size = 15;
        UUID empresaId = TenantContext.getEmpresaId();
        var base = PagamentoPixSpecification.comFiltros(empresaId, lojaId, status, de, ate, fornecedor);

        var pendentes = base.and((root, query, cb) ->
                root.get("status").in(StatusPix.PENDENTE, StatusPix.VENCIDO));
        var primeiro = pixRepository.findAll(pendentes,
                PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "vencimento", "fornecedor", "id")));
        if (primeiro.isEmpty()) return 0;

        long quantidadeAntes = pixRepository.count(base.and(antesDe(primeiro.getContent().get(0))));
        return (int) (quantidadeAntes / size);
    }

    /**
     * Itens que precedem {@code alvo} na ordenação da listagem: comparação
     * lexicográfica de (vencimento, fornecedor, id). Precisa acompanhar o
     * {@code @PageableDefault} do controller — se divergir, a página calculada
     * aqui deixa de ser a página em que o item realmente aparece.
     */
    private static Specification<PagamentoPix> antesDe(PagamentoPix alvo) {
        LocalDate vencimento = alvo.getVencimento();
        String fornecedor = alvo.getFornecedor();
        UUID id = alvo.getId();
        return (root, query, cb) -> cb.or(
                cb.lessThan(root.get("vencimento"), vencimento),
                cb.and(cb.equal(root.get("vencimento"), vencimento),
                        cb.lessThan(root.get("fornecedor"), fornecedor)),
                cb.and(cb.equal(root.get("vencimento"), vencimento),
                        cb.equal(root.get("fornecedor"), fornecedor),
                        cb.lessThan(root.get("id"), id)));
    }

    /**
     * Busca por ID com verificação de acesso à empresa.
     * Retorna a entidade (não DTO) para uso interno por outros métodos do service.
     */
    @Transactional(readOnly = true)
    public PagamentoPix buscarPorId(UUID id) {
        UUID empresaId = TenantContext.getEmpresaId();
        var pix = pixRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("PIX não encontrado"));
        if (!pix.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");
        return pix;
    }

    @Transactional
    public PixDTO criar(CriarPixRequest req) {
        UUID empresaId = TenantContext.getEmpresaId();
        var loja = lojaRepository.findById(req.lojaId())
                .orElseThrow(() -> new NotFoundException("Loja não encontrada"));
        if (!loja.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");

        var pix = PagamentoPix.builder()
                .empresa(loja.getEmpresa()).loja(loja)
                .fornecedor(req.fornecedor()).valor(req.valor())
                .vencimento(req.vencimento()).chavePix(req.chavePix())
                .tipoChave(req.tipoChave()).observacoes(req.observacoes())
                .build();
        pix = pixRepository.save(pix);
        auditoriaService.registrar("CRIAR", "PIX", pix.getId(), "Fornecedor: " + req.fornecedor());
        return PixDTO.from(pix);
    }

    @Transactional
    public PixDTO editar(UUID id, EditarPixRequest req) {
        UUID empresaId = TenantContext.getEmpresaId();
        var pix = pixRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("PIX não encontrado"));
        if (!pix.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");

        var loja = lojaRepository.findById(req.lojaId())
                .orElseThrow(() -> new NotFoundException("Loja não encontrada"));

        pix.setLoja(loja);
        pix.setFornecedor(req.fornecedor());
        pix.setValor(req.valor());
        pix.setVencimento(req.vencimento());
        pix.setChavePix(req.chavePix());
        pix.setTipoChave(req.tipoChave());
        pix.setObservacoes(req.observacoes());
        pix = pixRepository.save(pix);
        auditoriaService.registrar("EDITAR", "PIX", pix.getId(), "Fornecedor: " + req.fornecedor());
        return PixDTO.from(pix);
    }

    /**
     * Altera o status e registra {@code pagoEm} (quando PAGO) ou limpa (outros status).
     * Não valida a transição (ex: impedir CANCELADO → PAGO).
     */
    @Transactional
    public PixDTO mudarStatus(UUID id, StatusPix novoStatus) {
        UUID empresaId = TenantContext.getEmpresaId();
        var pix = pixRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("PIX não encontrado"));
        if (!pix.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");

        pix.setStatus(novoStatus);
        // Se voltar a ser PENDENTE/CANCELADO, limpa a data de pagamento anterior
        if (novoStatus == StatusPix.PAGO) {
            pix.setPagoEm(LocalDateTime.now());
        } else {
            pix.setPagoEm(null);
        }
        pix = pixRepository.save(pix);
        auditoriaService.registrar("MUDAR_STATUS", "PIX", pix.getId(), "Status: " + novoStatus);
        return PixDTO.from(pix);
    }

    /**
     * Exclui logicamente um PIX do banco (DELETE físico).
     * Remove o arquivo anexo do S3 antes se houver.
     */
    @Transactional
    public void excluir(UUID id) {
        UUID empresaId = TenantContext.getEmpresaId();
        var pix = pixRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("PIX não encontrado"));
        if (!pix.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");
        if (pix.getArquivoKey() != null) {
            arquivoService.deletar(pix.getArquivoKey());
        }
        pixRepository.delete(pix);
        auditoriaService.registrar("EXCLUIR", "PIX", id, "Fornecedor: " + pix.getFornecedor());
    }

    /**
     * Faz upload de um arquivo (comprovante, nota) associado ao PIX.
     * Substitui arquivo anterior se existir (deleta o antigo no S3 antes de salvar o novo).
     */
    @Transactional
    public PixDTO uploadArquivo(UUID id, MultipartFile arquivo) {
        UUID empresaId = TenantContext.getEmpresaId();
        var pix = pixRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("PIX não encontrado"));
        if (!pix.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");

        try {
            String key = arquivoService.upload(arquivo, TipoDocumento.PIX, pix.getLoja().getId());
            if (pix.getArquivoKey() != null) {
                arquivoService.deletar(pix.getArquivoKey());
            }
            pix.setArquivoKey(key);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao fazer upload do arquivo: " + e.getMessage());
        }

        pix = pixRepository.save(pix);
        auditoriaService.registrar("UPLOAD", "PIX", pix.getId(), "Arquivo: " + arquivo.getOriginalFilename());
        return PixDTO.from(pix);
    }

    /** Remove apenas a referência ao arquivo (key) sem deletar do S3 — usado quando o S3 já foi limpo. */
    @Transactional
    public void removerArquivoKey(UUID id) {
        UUID empresaId = TenantContext.getEmpresaId();
        var pix = pixRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("PIX não encontrado"));
        if (!pix.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");
        pix.setArquivoKey(null);
        pixRepository.save(pix);
    }
}