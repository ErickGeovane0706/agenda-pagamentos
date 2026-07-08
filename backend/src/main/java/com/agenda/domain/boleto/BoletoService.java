package com.agenda.domain.boleto;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Serviço com a lógica de negócio de {@link Boleto}.
 * <p>
 * Garante isolamento multi‑tenant: toda operação verifica se o recurso
 * pertence à empresa do usuário logado ({@link com.agenda.shared.TenantContext}).
 * Auditoria é registrada em todas as operações de escrita.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class BoletoService {

    private final BoletoRepository boletoRepository;
    private final LojaRepository lojaRepository;
    private final ArquivoService arquivoService;
    private final AuditoriaService auditoriaService;

    /**
     * Lista paginada com filtros dinâmicos via {@link BoletoSpecification}.
     * O fornecedor é convertido para lower‑case para busca case‑insensitive.
     */
    @Transactional(readOnly = true)
    public Page<BoletoDTO> listar(UUID lojaId, StatusBoleto status, LocalDate de, LocalDate ate, String fornecedor, Pageable pageable) {
        UUID empresaId = TenantContext.getEmpresaId();
        String padrao = fornecedor != null ? "%" + fornecedor.toLowerCase() + "%" : null;
        var spec = BoletoSpecification.comFiltros(empresaId, lojaId, status, de, ate, fornecedor);
        return boletoRepository.findAll(spec, pageable)
                .map(BoletoDTO::from);
    }

    /**
     * Índice (0-based) da primeira página que contém um boleto pendente ou vencido,
     * considerando os mesmos filtros e a mesma ordenação (vencimento ASC) da listagem.
     * <p>
     * Estratégia: acha o menor vencimento entre os pendentes/vencidos e conta quantos
     * itens (de todos os status, respeitando os filtros) vêm antes dele. A página é
     * {@code floor(quantidadeAntes / size)}. Se não houver pendência, retorna 0.
     * </p>
     */
    @Transactional(readOnly = true)
    public int paginaPendente(UUID lojaId, StatusBoleto status, LocalDate de, LocalDate ate, String fornecedor, int size) {
        if (size <= 0) size = 15;
        UUID empresaId = TenantContext.getEmpresaId();
        var base = BoletoSpecification.comFiltros(empresaId, lojaId, status, de, ate, fornecedor);

        var pendentes = base.and((root, query, cb) ->
                root.get("status").in(StatusBoleto.PENDENTE, StatusBoleto.VENCIDO));
        var primeiro = boletoRepository.findAll(pendentes,
                PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "vencimento")));
        if (primeiro.isEmpty()) return 0;

        LocalDate menorVencimento = primeiro.getContent().get(0).getVencimento();
        var antes = base.and((root, query, cb) -> cb.lessThan(root.get("vencimento"), menorVencimento));
        long quantidadeAntes = boletoRepository.count(antes);
        return (int) (quantidadeAntes / size);
    }

    /**
     * Busca por ID com verificação de tenant — usado internamente por outros serviços
     * e pelo controller para operações de arquivo.
     */
    @Transactional(readOnly = true)
    public Boleto buscarPorId(UUID id) {
        UUID empresaId = TenantContext.getEmpresaId();
        var boleto = boletoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Boleto não encontrado"));
        if (!boleto.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");
        return boleto;
    }

    /**
     * Cria um novo boleto.
     * A loja é validada e sua empresa é usada como tenant do boleto.
     * Status inicial é sempre PENDENTE (default do {@link Boleto#status}).
     */
    @Transactional
    public BoletoDTO criar(CriarBoletoRequest req) {
        UUID empresaId = TenantContext.getEmpresaId();
        var loja = lojaRepository.findById(req.lojaId())
                .orElseThrow(() -> new NotFoundException("Loja não encontrada"));
        if (!loja.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");

        var boleto = Boleto.builder()
                .empresa(loja.getEmpresa())
                .loja(loja)
                .fornecedor(req.fornecedor())
                .valor(req.valor())
                .vencimento(req.vencimento())
                .codigoBarras(req.codigoBarras())
                .observacoes(req.observacoes())
                .build();
        boleto = boletoRepository.save(boleto);
        auditoriaService.registrar("CRIAR", "BOLETO", boleto.getId(), "Fornecedor: " + req.fornecedor());
        return BoletoDTO.from(boleto);
    }

    /**
     * Edita todos os campos do boleto (PUT lógico). A loja pode ser alterada.
     * O status NÃO é alterado aqui — use {@link #mudarStatus}.
     */
    @Transactional
    public BoletoDTO editar(UUID id, EditarBoletoRequest req) {
        UUID empresaId = TenantContext.getEmpresaId();
        var boleto = boletoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Boleto não encontrado"));
        if (!boleto.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");

        var loja = lojaRepository.findById(req.lojaId())
                .orElseThrow(() -> new NotFoundException("Loja não encontrada"));

        boleto.setLoja(loja);
        boleto.setFornecedor(req.fornecedor());
        boleto.setValor(req.valor());
        boleto.setVencimento(req.vencimento());
        boleto.setCodigoBarras(req.codigoBarras());
        boleto.setObservacoes(req.observacoes());
        boleto = boletoRepository.save(boleto);
        auditoriaService.registrar("EDITAR", "BOLETO", boleto.getId(), "Fornecedor: " + req.fornecedor());
        return BoletoDTO.from(boleto);
    }

    /**
     * Altera o status do boleto.
     * Quando {@code PAGO}, preenche {@code pagoEm} com o instante atual.
     * Quando qualquer outro status, limpa {@code pagoEm} (reversão).
     */
    @Transactional
    public BoletoDTO mudarStatus(UUID id, StatusBoleto novoStatus) {
        UUID empresaId = TenantContext.getEmpresaId();
        var boleto = boletoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Boleto não encontrado"));
        if (!boleto.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");

        boleto.setStatus(novoStatus);
        if (novoStatus == StatusBoleto.PAGO) {
            boleto.setPagoEm(LocalDateTime.now());
        } else {
            boleto.setPagoEm(null);
        }
        boleto = boletoRepository.save(boleto);
        auditoriaService.registrar("MUDAR_STATUS", "BOLETO", boleto.getId(), "Status: " + novoStatus);
        return BoletoDTO.from(boleto);
    }

    /**
     * Exclui o boleto fisicamente (DELETE).
     * Nota: o arquivo vinculado (se existir) não é removido do storage aqui;
     * a deleção do arquivo deve ser chamada explicitamente pelo controller.
     */
    @Transactional
    public void excluir(UUID id) {
        UUID empresaId = TenantContext.getEmpresaId();
        var boleto = boletoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Boleto não encontrado"));
        if (!boleto.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");
        boletoRepository.delete(boleto);
        auditoriaService.registrar("EXCLUIR", "BOLETO", id, "Fornecedor: " + boleto.getFornecedor());
    }

    /**
     * Faz upload do arquivo do boleto.
     * Se já existia um arquivo anterior, ele é deletado do storage (substituição).
     */
    @Transactional
    public BoletoDTO uploadArquivo(UUID id, MultipartFile arquivo) {
        UUID empresaId = TenantContext.getEmpresaId();
        var boleto = boletoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Boleto não encontrado"));
        if (!boleto.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");

        try {
            String key = arquivoService.upload(arquivo, TipoDocumento.BOLETO, boleto.getLoja().getId());
            if (boleto.getArquivoKey() != null) {
                arquivoService.deletar(boleto.getArquivoKey());
            }
            boleto.setArquivoKey(key);
            boleto.setNomeArquivo(arquivo.getOriginalFilename());
        } catch (IOException e) {
            throw new RuntimeException("Erro ao fazer upload do arquivo: " + e.getMessage());
        }

        boleto = boletoRepository.save(boleto);
        auditoriaService.registrar("UPLOAD", "BOLETO", boleto.getId(), "Arquivo: " + arquivo.getOriginalFilename());
        return BoletoDTO.from(boleto);
    }

    /**
     * Apenas limpa a referência do arquivo no boleto (não deleta do storage).
     * Usado pelo controller após já ter deletado o arquivo no storage.
     */
    @Transactional
    public void removerArquivoKey(UUID id) {
        UUID empresaId = TenantContext.getEmpresaId();
        var boleto = boletoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Boleto não encontrado"));
        if (!boleto.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");
        boleto.setArquivoKey(null);
        boleto.setNomeArquivo(null);
        boletoRepository.save(boleto);
    }
}