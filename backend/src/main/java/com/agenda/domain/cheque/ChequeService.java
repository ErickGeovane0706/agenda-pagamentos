package com.agenda.domain.cheque;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.domain.arquivo.ArquivoService;
import com.agenda.domain.arquivo.TipoDocumento;
import com.agenda.domain.banco.BancoRepository;
import com.agenda.domain.loja.LojaRepository;
import com.agenda.shared.TenantContext;
import com.agenda.shared.exception.AccessDeniedException;
import com.agenda.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChequeService {

    private final ChequeRepository chequeRepository;
    private final LojaRepository lojaRepository;
    private final BancoRepository bancoRepository;
    private final ArquivoService arquivoService;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public Page<ChequeDTO> listar(UUID lojaId, StatusCheque status, LocalDate de, LocalDate ate, Pageable pageable) {
        UUID empresaId = TenantContext.getEmpresaId();
        return chequeRepository.listar(empresaId, lojaId, status, de, ate, pageable)
            .map(ChequeDTO::from);
    }

    @Transactional(readOnly = true)
    public Cheque buscarPorId(UUID id) {
        return chequeRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Cheque não encontrado"));
    }

    @Transactional
    public ChequeDTO criar(CriarChequeRequest req) {
        UUID empresaId = TenantContext.getEmpresaId();
        var loja = lojaRepository.findById(req.lojaId())
            .orElseThrow(() -> new NotFoundException("Loja não encontrada"));
        if (!loja.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");

        var banco = req.bancoId() != null
            ? bancoRepository.findById(req.bancoId()).orElse(null)
            : null;

        var cheque = Cheque.builder()
            .empresa(loja.getEmpresa()).loja(loja).banco(banco)
            .fornecedor(req.fornecedor()).valor(req.valor())
            .vencimento(req.vencimento()).numeroCheque(req.numeroCheque())
            .observacoes(req.observacoes()).build();
        cheque = chequeRepository.save(cheque);
        auditoriaService.registrar("CRIAR", "CHEQUE", cheque.getId(), "Fornecedor: " + req.fornecedor());
        return ChequeDTO.from(cheque);
    }

    @Transactional
    public ChequeDTO editar(UUID id, EditarChequeRequest req) {
        UUID empresaId = TenantContext.getEmpresaId();
        var cheque = chequeRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Cheque não encontrado"));
        if (!cheque.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");

        var loja = lojaRepository.findById(req.lojaId())
            .orElseThrow(() -> new NotFoundException("Loja não encontrada"));
        var banco = req.bancoId() != null
            ? bancoRepository.findById(req.bancoId()).orElse(null)
            : null;

        cheque.setLoja(loja);
        cheque.setBanco(banco);
        cheque.setFornecedor(req.fornecedor());
        cheque.setValor(req.valor());
        cheque.setVencimento(req.vencimento());
        cheque.setNumeroCheque(req.numeroCheque());
        cheque.setObservacoes(req.observacoes());
        cheque = chequeRepository.save(cheque);
        auditoriaService.registrar("EDITAR", "CHEQUE", cheque.getId(), "Fornecedor: " + req.fornecedor());
        return ChequeDTO.from(cheque);
    }

    @Transactional
    public ChequeDTO mudarStatus(UUID id, StatusCheque novoStatus) {
        UUID empresaId = TenantContext.getEmpresaId();
        var cheque = chequeRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Cheque não encontrado"));
        if (!cheque.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");

        cheque.setStatus(novoStatus);
        if (novoStatus == StatusCheque.COMPENSADO) {
            cheque.setCompensadoEm(LocalDateTime.now());
        } else {
            cheque.setCompensadoEm(null);
        }
        cheque = chequeRepository.save(cheque);
        auditoriaService.registrar("MUDAR_STATUS", "CHEQUE", cheque.getId(), "Status: " + novoStatus);
        return ChequeDTO.from(cheque);
    }

    @Transactional
    public void excluir(UUID id) {
        UUID empresaId = TenantContext.getEmpresaId();
        var cheque = chequeRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Cheque não encontrado"));
        if (!cheque.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");
        if (cheque.getArquivoKey() != null) {
            arquivoService.deletar(cheque.getArquivoKey());
        }
        chequeRepository.delete(cheque);
        auditoriaService.registrar("EXCLUIR", "CHEQUE", id, "Fornecedor: " + cheque.getFornecedor());
    }

    @Transactional
    public ChequeDTO uploadArquivo(UUID id, MultipartFile arquivo) {
        UUID empresaId = TenantContext.getEmpresaId();
        var cheque = chequeRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Cheque não encontrado"));
        if (!cheque.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");

        try {
            String key = arquivoService.upload(arquivo, TipoDocumento.CHEQUE, cheque.getLoja().getId());
            if (cheque.getArquivoKey() != null) {
                arquivoService.deletar(cheque.getArquivoKey());
            }
            cheque.setArquivoKey(key);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao fazer upload do arquivo: " + e.getMessage());
        }

        cheque = chequeRepository.save(cheque);
        auditoriaService.registrar("UPLOAD", "CHEQUE", cheque.getId(), "Arquivo: " + arquivo.getOriginalFilename());
        return ChequeDTO.from(cheque);
    }

    @Transactional
    public void removerArquivoKey(UUID id) {
        UUID empresaId = TenantContext.getEmpresaId();
        var cheque = chequeRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Cheque não encontrado"));
        if (!cheque.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");
        cheque.setArquivoKey(null);
        chequeRepository.save(cheque);
    }
}
