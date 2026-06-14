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
public class PixService {

    private final PagamentoPixRepository pixRepository;
    private final LojaRepository lojaRepository;
    private final ArquivoService arquivoService;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public Page<PixDTO> listar(UUID lojaId, StatusPix status, LocalDate de, LocalDate ate, Pageable pageable) {
        UUID empresaId = TenantContext.getEmpresaId();
        return pixRepository.listar(empresaId, lojaId, status, de, ate, pageable)
            .map(PixDTO::from);
    }

    @Transactional(readOnly = true)
    public PagamentoPix buscarPorId(UUID id) {
        return pixRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("PIX não encontrado"));
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

    @Transactional
    public PixDTO mudarStatus(UUID id, StatusPix novoStatus) {
        UUID empresaId = TenantContext.getEmpresaId();
        var pix = pixRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("PIX não encontrado"));
        if (!pix.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");

        pix.setStatus(novoStatus);
        if (novoStatus == StatusPix.PAGO) {
            pix.setPagoEm(LocalDateTime.now());
        } else {
            pix.setPagoEm(null);
        }
        pix = pixRepository.save(pix);
        auditoriaService.registrar("MUDAR_STATUS", "PIX", pix.getId(), "Status: " + novoStatus);
        return PixDTO.from(pix);
    }

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
