package com.agenda.domain.banco;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.domain.empresa.EmpresaRepository;
import com.agenda.shared.TenantContext;
import com.agenda.shared.exception.AccessDeniedException;
import com.agenda.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service de bancos. Operações CRUD com escopo por empresa
 * (tenant isolado via TenantContext). Cada operação de
 * escrita gera registro de auditoria. Verifica permissão
 * de acesso ao banco antes de qualquer operação.
 */
@Service
@RequiredArgsConstructor
public class BancoService {

    private final BancoRepository bancoRepository;
    private final EmpresaRepository empresaRepository;
    private final AuditoriaService auditoriaService;

    /**
     * Lista todos os bancos da empresa logada, ordenados por nome.
     * Cada banco é isolado por tenant (empresaId).
     */
    @Transactional(readOnly = true)
    public List<BancoDTO> listar() {
        UUID empresaId = TenantContext.getEmpresaId();
        return bancoRepository.findByEmpresaIdOrderByNome(empresaId)
            .stream().map(BancoDTO::from).toList();
    }

    /**
     * Cria um novo banco vinculado à empresa do tenant atual.
     * Registra auditoria com ação "CRIAR".
     */
    @Transactional
    public BancoDTO criar(CriarBancoRequest req) {
        UUID empresaId = TenantContext.getEmpresaId();
        var empresa = empresaRepository.getReferenceById(empresaId);
        var banco = Banco.builder().empresa(empresa).nome(req.nome()).codigo(req.codigo()).build();
        banco = bancoRepository.save(banco);
        auditoriaService.registrar("CRIAR", "BANCO", banco.getId(), "Nome: " + req.nome());
        return BancoDTO.from(banco);
    }

    /**
     * Edita nome e código de um banco. Valida pertinência ao tenant
     * (AccessDeniedException se o banco não pertencer à empresa logada).
     * Registra auditoria com ação "EDITAR".
     */
    @Transactional
    public BancoDTO editar(UUID id, EditarBancoRequest req) {
        UUID empresaId = TenantContext.getEmpresaId();
        var banco = bancoRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Banco não encontrado"));
        if (!banco.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");
        banco.setNome(req.nome());
        banco.setCodigo(req.codigo());
        banco = bancoRepository.save(banco);
        auditoriaService.registrar("EDITAR", "BANCO", banco.getId(), "Nome: " + req.nome());
        return BancoDTO.from(banco);
    }

    /**
     * Exclui um banco. Valida pertinência ao tenant.
     * Registra auditoria com ação "EXCLUIR".
     */
    @Transactional
    public void excluir(UUID id) {
        UUID empresaId = TenantContext.getEmpresaId();
        var banco = bancoRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Banco não encontrado"));
        if (!banco.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");
        bancoRepository.delete(banco);
        auditoriaService.registrar("EXCLUIR", "BANCO", id, "Nome: " + banco.getNome());
    }
}
