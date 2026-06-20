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
public class BoletoService {

    private final BoletoRepository boletoRepository;
    private final LojaRepository lojaRepository;
    private final ArquivoService arquivoService;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public Page<BoletoDTO> listar(UUID lojaId, StatusBoleto status, LocalDate de, LocalDate ate, String fornecedor, Pageable pageable) {
        UUID empresaId = TenantContext.getEmpresaId();
        String padrao = fornecedor != null ? "%" + fornecedor.toLowerCase() + "%" : null;
        var spec = BoletoSpecification.comFiltros(empresaId, lojaId, status, de, ate, fornecedor);
        return boletoRepository.findAll(spec, pageable)
                .map(BoletoDTO::from);
    }

    @Transactional(readOnly = true)
    public Boleto buscarPorId(UUID id) {
        return boletoRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Boleto não encontrado"));
    }

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

    @Transactional
    public void excluir(UUID id) {
        UUID empresaId = TenantContext.getEmpresaId();
        var boleto = boletoRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Boleto não encontrado"));
        if (!boleto.getEmpresa().getId().equals(empresaId)) throw new AccessDeniedException("Acesso negado");
        boletoRepository.delete(boleto);
        auditoriaService.registrar("EXCLUIR", "BOLETO", id, "Fornecedor: " + boleto.getFornecedor());
    }

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
