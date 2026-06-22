package com.agenda.domain.coluna;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.shared.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service de colunas extras e seus valores. O schema é dinâmico:
 * cada empresa define suas próprias colunas; os valores são
 * armazenados em tabela separada (valores_extras) com chave
 * composta (registroId + tipoPagamento + colunaId).
 */
@Service
@RequiredArgsConstructor
public class ColunaExtraService {

    private final ColunaExtraRepository colunaExtraRepository;
    private final ValorExtraRepository valorExtraRepository;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public List<ColunaExtraDTO> listar(TipoPagamento tipoPagamento) {
        UUID empresaId = TenantContext.getEmpresaId();
        return colunaExtraRepository
            .findByEmpresaIdAndTipoPagamentoAndAtivoTrueOrderByOrdemAsc(empresaId, tipoPagamento)
            .stream()
            .map(ColunaExtraDTO::from)
            .toList();
    }

    @Transactional
    public ColunaExtraDTO criar(CriarColunaExtraRequest req) {
        UUID empresaId = TenantContext.getEmpresaId();
        var coluna = ColunaExtra.builder()
            .empresaId(empresaId)
            .tipoPagamento(req.tipoPagamento())
            .nome(req.nome())
            .tipoDado(req.tipoDado() != null ? req.tipoDado() : TipoDado.TEXTO)
            .obrigatorio(req.obrigatorio() != null && req.obrigatorio())
            .ordem(req.ordem() != null ? req.ordem() : 0)
            .build();
        coluna = colunaExtraRepository.save(coluna);
        auditoriaService.registrar("CRIAR", "COLUNA_EXTRA", coluna.getId(), "Nome: " + req.nome());
        return ColunaExtraDTO.from(coluna);
    }

    @Transactional
    public void excluir(UUID id) {
        UUID empresaId = TenantContext.getEmpresaId();
        var coluna = colunaExtraRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Coluna não encontrada"));
        if (!coluna.getEmpresaId().equals(empresaId)) throw new RuntimeException("Acesso negado");
        colunaExtraRepository.delete(coluna);
        auditoriaService.registrar("EXCLUIR", "COLUNA_EXTRA", id, "Nome: " + coluna.getNome());
    }

    public Map<UUID, String> listarValores(UUID registroId, TipoPagamento tipoPagamento) {
        return valorExtraRepository.findByRegistroIdAndTipoPagamento(registroId, tipoPagamento)
            .stream()
            .collect(Collectors.toMap(ValorExtra::getColunaId, ValorExtra::getValor));
    }

    @Transactional
    public void salvarValores(TipoPagamento tipoPagamento, UUID registroId, Map<UUID, String> valores) {
        valorExtraRepository.deleteByRegistroIdAndTipoPagamento(registroId, tipoPagamento);
        if (valores == null) return;
        for (var entry : valores.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) continue;
            valorExtraRepository.save(ValorExtra.builder()
                .colunaId(entry.getKey())
                .registroId(registroId)
                .tipoPagamento(tipoPagamento)
                .valor(entry.getValue())
                .build());
        }
    }
}