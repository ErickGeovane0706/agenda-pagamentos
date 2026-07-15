package com.agenda.domain.assinatura;

import com.agenda.domain.empresa.Empresa;
import com.agenda.shared.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Regras de acesso por assinatura. São exatamente DUAS checagens no sistema:
 * criar loja além do contratado (podeCriarLoja) e empresa inadimplente ou
 * cancelada em modo somente-leitura (podeAcessar) — nada de matriz de
 * entitlements por feature.
 *
 * A checagem é sempre lookup no banco (query por unique de empresa_id),
 * nunca claim no JWT: se o plano vivesse no token, o cliente pagaria e
 * continuaria bloqueado até relogar.
 */
@Service
public class AssinaturaService {

    private final AssinaturaRepository assinaturaRepository;

    /**
     * Dias de tolerância após vigenteAte antes de bloquear o acesso.
     * Configurável porque a cobrança é por Pix/boleto manual — a compensação
     * pode levar dias.
     */
    private final int carenciaDias;

    public AssinaturaService(AssinaturaRepository assinaturaRepository,
                             @Value("${assinatura.carencia-dias}") int carenciaDias) {
        this.assinaturaRepository = assinaturaRepository;
        this.carenciaDias = carenciaDias;
    }

    /**
     * A empresa pode operar (escrever, usar o agente, receber lembretes)?
     * TRIAL e ATIVA sim, desde que a vigência (quando controlada) não tenha
     * estourado a carência. Empresa sem assinatura não pode — não deve
     * acontecer (seed da V17 + trial criado junto com a empresa), mas se
     * acontecer o seguro é bloquear, não liberar.
     */
    @Transactional(readOnly = true)
    public boolean podeAcessar(UUID empresaId) {
        return assinaturaRepository.findByEmpresaId(empresaId)
            .map(this::acessivel)
            .orElse(false);
    }

    private boolean acessivel(Assinatura a) {
        if (a.getStatus() != StatusAssinatura.TRIAL && a.getStatus() != StatusAssinatura.ATIVA) {
            return false;
        }
        return a.getVigenteAte() == null
            || !LocalDate.now().isAfter(a.getVigenteAte().plusDays(carenciaDias));
    }

    /**
     * A empresa pode criar mais uma loja? Conta TODAS as lojas existentes
     * (não só as ativas — desativar/reativar não libera vaga).
     */
    @Transactional(readOnly = true)
    public boolean podeCriarLoja(UUID empresaId, long lojasExistentes) {
        return assinaturaRepository.findByEmpresaId(empresaId)
            .map(a -> lojasExistentes < a.getLojasContratadas())
            .orElse(false);
    }

    /**
     * Assinatura inicial de uma empresa recém-criada: TRIAL com 1 loja.
     * Sem isso a empresa nova nasceria sem assinatura e ficaria bloqueada
     * antes mesmo de criar a primeira loja. O MASTER ajusta depois.
     */
    @Transactional
    public Assinatura criarTrial(Empresa empresa) {
        return assinaturaRepository.save(Assinatura.builder()
            .empresa(empresa)
            .status(StatusAssinatura.TRIAL)
            .lojasContratadas(1)
            .build());
    }

    @Transactional(readOnly = true)
    public List<AssinaturaDTO> listar() {
        return assinaturaRepository.findAll().stream()
            .map(AssinaturaDTO::from)
            .toList();
    }

    /**
     * "Virada de linha na mão" da Fase 1: o MASTER ativa/suspende/cancela e
     * ajusta lojas contratadas e vigência após confirmar o pagamento do
     * Payment Link. Na Fase 2 o webhook do gateway passa a fazer isso.
     */
    @Transactional
    public AssinaturaDTO atualizar(UUID empresaId, AtualizarAssinaturaRequest req) {
        var assinatura = assinaturaRepository.findByEmpresaId(empresaId)
            .orElseThrow(() -> new NotFoundException("Assinatura não encontrada para a empresa"));

        assinatura.setStatus(req.status());
        assinatura.setLojasContratadas(req.lojasContratadas());
        assinatura.setVigenteAte(req.vigenteAte());
        return AssinaturaDTO.from(assinaturaRepository.save(assinatura));
    }
}
