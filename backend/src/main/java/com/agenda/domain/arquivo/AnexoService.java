package com.agenda.domain.arquivo;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Anexos de um lançamento — boleto, comprovante, pedido, nota fiscal — até
 * {@link #MAXIMO_POR_LANCAMENTO} por lançamento.
 * <p>
 * Não confere tenant: quem chama já carregou o lançamento pelo
 * {@code buscarPorId} do service dele, que confere. Aqui só se garante que o
 * anexo pedido pertence ao lançamento informado.
 */
@Service
@RequiredArgsConstructor
public class AnexoService {

    public static final int MAXIMO_POR_LANCAMENTO = 5;

    private final AnexoRepository anexoRepository;
    private final ArquivoService arquivoService;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public List<AnexoDTO> listar(TipoDocumento tipo, UUID lancamentoId) {
        return doLancamento(tipo, lancamentoId).stream().map(AnexoDTO::from).toList();
    }

    @Transactional
    public AnexoDTO adicionar(TipoDocumento tipo, UUID lancamentoId, UUID lojaId, MultipartFile arquivo) {
        if (doLancamento(tipo, lancamentoId).size() >= MAXIMO_POR_LANCAMENTO) {
            throw new IllegalArgumentException("Limite de " + MAXIMO_POR_LANCAMENTO
                    + " arquivos por lançamento. Remova um para anexar outro.");
        }

        String key;
        try {
            key = arquivoService.upload(arquivo, tipo, lojaId);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao fazer upload do arquivo: " + e.getMessage());
        }

        String nome = arquivo.getOriginalFilename();
        if (nome != null && nome.length() > 300) nome = nome.substring(0, 300);

        var anexo = Anexo.builder().arquivoKey(key).nomeArquivo(nome).build();
        switch (tipo) {
            case BOLETO -> anexo.setBoletoId(lancamentoId);
            case PIX -> anexo.setPixId(lancamentoId);
            case CHEQUE -> anexo.setChequeId(lancamentoId);
        }
        anexo = anexoRepository.save(anexo);
        auditoriaService.registrar("UPLOAD", tipo.name(), lancamentoId, "Arquivo: " + nome);
        return AnexoDTO.from(anexo);
    }

    @Transactional(readOnly = true)
    public String gerarUrlTemporaria(TipoDocumento tipo, UUID lancamentoId, UUID anexoId) {
        return arquivoService.gerarUrlTemporaria(buscar(tipo, lancamentoId, anexoId).getArquivoKey());
    }

    @Transactional
    public void remover(TipoDocumento tipo, UUID lancamentoId, UUID anexoId) {
        var anexo = buscar(tipo, lancamentoId, anexoId);
        arquivoService.deletar(anexo.getArquivoKey());
        anexoRepository.delete(anexo);
    }

    /**
     * Apaga do R2 os arquivos de um lançamento que vai ser excluído. As linhas
     * saem sozinhas pelo ON DELETE CASCADE; o arquivo no bucket não — e costuma
     * trazer dado bancário.
     */
    public void removerArquivosDo(TipoDocumento tipo, UUID lancamentoId) {
        doLancamento(tipo, lancamentoId).forEach(a -> arquivoService.deletar(a.getArquivoKey()));
    }

    private Anexo buscar(TipoDocumento tipo, UUID lancamentoId, UUID anexoId) {
        return anexoRepository.findById(anexoId)
                .filter(a -> lancamentoId.equals(lancamentoDe(tipo, a)))
                .orElseThrow(() -> new NotFoundException("Arquivo não encontrado"));
    }

    private List<Anexo> doLancamento(TipoDocumento tipo, UUID lancamentoId) {
        return switch (tipo) {
            case BOLETO -> anexoRepository.findByBoletoIdOrderByCriadoEmAsc(lancamentoId);
            case PIX -> anexoRepository.findByPixIdOrderByCriadoEmAsc(lancamentoId);
            case CHEQUE -> anexoRepository.findByChequeIdOrderByCriadoEmAsc(lancamentoId);
        };
    }

    private static UUID lancamentoDe(TipoDocumento tipo, Anexo a) {
        return switch (tipo) {
            case BOLETO -> a.getBoletoId();
            case PIX -> a.getPixId();
            case CHEQUE -> a.getChequeId();
        };
    }
}
