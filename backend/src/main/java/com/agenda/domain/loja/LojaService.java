package com.agenda.domain.loja;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.domain.assinatura.AssinaturaService;
import com.agenda.domain.empresa.EmpresaRepository;
import com.agenda.shared.TenantContext;
import com.agenda.shared.exception.AccessDeniedException;
import com.agenda.shared.exception.NotFoundException;
import com.agenda.shared.exception.PagamentoRequeridoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service de Loja. Cada operação valida o vínculo com a empresa
 * do tenant logado — impede que um usuário acesse lojas de outra
 * empresa.
 */
@Service
@RequiredArgsConstructor
public class LojaService {

    private final LojaRepository lojaRepository;
    private final EmpresaRepository empresaRepository;
    private final AuditoriaService auditoriaService;
    private final AssinaturaService assinaturaService;

    @Transactional(readOnly = true)
    public List<LojaDTO> listar() {
        UUID empresaId = TenantContext.getEmpresaId();
        return lojaRepository.findByEmpresaIdOrderByNome(empresaId)
            .stream().map(LojaDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public LojaDTO buscar(UUID id) {
        UUID empresaId = TenantContext.getEmpresaId();
        var loja = lojaRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Loja não encontrada"));
        if (!loja.getEmpresa().getId().equals(empresaId)) {
            throw new AccessDeniedException("Acesso negado");
        }
        return LojaDTO.from(loja);
    }

    @Transactional
    public LojaDTO criar(CriarLojaRequest req) {
        return criarNaEmpresa(TenantContext.getEmpresaId(), req);
    }

    /**
     * Cria a loja de uma empresa informada explicitamente, em vez de tirá-la do
     * {@code TenantContext}. Existe para o provisionamento do cadastro público,
     * onde não há JWT e o ThreadLocal está — deliberadamente — vazio: mantê-lo
     * nulo é o que faz a auditoria se abster durante a transação, já que ela
     * roda em {@code REQUIRES_NEW} e não enxergaria a empresa ainda não
     * commitada (a FK {@code auditoria.empresa_id} estouraria).
     * <p>
     * O gate de assinatura continua valendo igual — esta não é uma porta que
     * escapa da regra, só uma que não depende do ThreadLocal.
     */
    @Transactional
    public LojaDTO criarNaEmpresa(UUID empresaId, CriarLojaRequest req) {
        // Gate de assinatura: conta TODAS as lojas (ativas ou não — desativar
        // não libera vaga) contra o contratado. Única porta de criação de loja.
        long lojasExistentes = lojaRepository.countByEmpresaId(empresaId);
        if (!assinaturaService.podeCriarLoja(empresaId, lojasExistentes)) {
            throw new PagamentoRequeridoException(
                "Sua assinatura não cobre mais lojas (você já tem " + lojasExistentes
                    + "). Ajuste a assinatura para adicionar outra.");
        }

        var empresa = empresaRepository.getReferenceById(empresaId);

        var loja = Loja.builder()
            .empresa(empresa)
            .nome(req.nome())
            .cnpj(req.cnpj())
            .descricao(req.descricao())
            .cor(req.cor() != null ? req.cor() : "#3B82F6")
            .build();
        loja = lojaRepository.save(loja);
        auditoriaService.registrar("CRIAR", "LOJA", loja.getId(), "Nome: " + req.nome());
        return LojaDTO.from(loja);
    }

    @Transactional
    public LojaDTO editar(UUID id, EditarLojaRequest req) {
        UUID empresaId = TenantContext.getEmpresaId();
        var loja = lojaRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Loja não encontrada"));
        if (!loja.getEmpresa().getId().equals(empresaId)) {
            throw new AccessDeniedException("Acesso negado");
        }

        loja.setNome(req.nome());
        loja.setCnpj(req.cnpj());
        loja.setDescricao(req.descricao());
        loja.setCor(req.cor() != null ? req.cor() : loja.getCor());
        loja = lojaRepository.save(loja);
        auditoriaService.registrar("EDITAR", "LOJA", loja.getId(), "Nome: " + req.nome());
        return LojaDTO.from(loja);
    }

    @Transactional
    public void excluir(UUID id) {
        UUID empresaId = TenantContext.getEmpresaId();
        var loja = lojaRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Loja não encontrada"));
        if (!loja.getEmpresa().getId().equals(empresaId)) {
            throw new AccessDeniedException("Acesso negado");
        }
        lojaRepository.delete(loja);
        auditoriaService.registrar("EXCLUIR", "LOJA", id, "Nome: " + loja.getNome());
    }
}
