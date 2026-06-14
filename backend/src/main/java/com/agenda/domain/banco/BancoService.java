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

@Service
@RequiredArgsConstructor
public class BancoService {

    private final BancoRepository bancoRepository;
    private final EmpresaRepository empresaRepository;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public List<BancoDTO> listar() {
        UUID empresaId = TenantContext.getEmpresaId();
        return bancoRepository.findByEmpresaIdOrderByNome(empresaId)
            .stream().map(BancoDTO::from).toList();
    }

    @Transactional
    public BancoDTO criar(CriarBancoRequest req) {
        UUID empresaId = TenantContext.getEmpresaId();
        var empresa = empresaRepository.getReferenceById(empresaId);
        var banco = Banco.builder().empresa(empresa).nome(req.nome()).codigo(req.codigo()).build();
        banco = bancoRepository.save(banco);
        auditoriaService.registrar("CRIAR", "BANCO", banco.getId(), "Nome: " + req.nome());
        return BancoDTO.from(banco);
    }

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
