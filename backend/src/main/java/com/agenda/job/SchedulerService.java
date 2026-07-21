package com.agenda.job;

import com.agenda.domain.auth.SenhaResetService;
import com.agenda.domain.boleto.BoletoRepository;
import com.agenda.domain.cheque.ChequeRepository;
import com.agenda.domain.empresa.EmpresaRepository;
import com.agenda.domain.pix.PagamentoPixRepository;
import com.agenda.domain.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Job agendado (03:00 todos os dias) que processa exclusões lógicas (LGPD).
 * Dois fluxos independentes:
 * <ul>
 *   <li><b>Empresa</b>: anonimiza dados pessoais dos usuários (nome, email,
 *       hash da senha) e financeiros (fornecedor, observações) de boletos,
 *       PIX e cheques, e marca a empresa como excluída.</li>
 *   <li><b>Usuário individual</b>: anonimiza apenas os usuários que pediram
 *       exclusão da própria conta, sem tocar na empresa nem em outros
 *       usuários ({@code anonimizarUsuariosIndividuais}).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulerService {

    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final BoletoRepository boletoRepository;
    private final PagamentoPixRepository pagamentoPixRepository;
    private final ChequeRepository chequeRepository;
    private final SenhaResetService senhaResetService;

    /**
     * Remove tokens de redefinição de senha vencidos ou já usados.
     * Roda 20 minutos depois das exclusões para não disputar a janela das 03:00.
     */
    @Scheduled(cron = "0 20 3 * * *")
    public void limparTokensDeSenha() {
        int removidos = senhaResetService.limparExpirados();
        log.info("Tokens de redefinição de senha removidos: {}", removidos);
    }

    @Transactional
    @Scheduled(cron = "0 0 3 * * *")
    public void processarExclusoes() {
        log.info("Processando solicitações de exclusão...");
        var empresas = empresaRepository.findBySolicitouExclusaoTrueAndExcluidoEmIsNull();

        for (var empresa : empresas) {
            anonimizarUsuarios(empresa);
            anonimizarRegistrosFinanceiros(empresa);
            empresa.setExcluidoEm(LocalDateTime.now());
            empresaRepository.save(empresa);
            log.info("Empresa {} anonimizada", empresa.getId());
        }

        anonimizarUsuariosIndividuais();

        if (empresas.isEmpty()) {
            log.info("Nenhuma exclusão pendente.");
        }
    }

    private void anonimizarUsuarios(com.agenda.domain.empresa.Empresa empresa) {
        for (var usuario : usuarioRepository.findByEmpresaId(empresa.getId())) {
            anonimizarUsuario(usuario);
            usuarioRepository.save(usuario);
        }
    }

    /**
     * Anonimiza usuários que pediram exclusão individual (sem apagar a empresa).
     * Usuários de empresas já anonimizadas acima têm excluidoEm preenchido e são
     * ignorados pela query (excluidoEm IS NULL) — sem processamento duplicado.
     */
    private void anonimizarUsuariosIndividuais() {
        for (var usuario : usuarioRepository.findBySolicitouExclusaoTrueAndExcluidoEmIsNull()) {
            anonimizarUsuario(usuario);
            usuarioRepository.save(usuario);
            log.info("Usuário {} anonimizado (exclusão individual)", usuario.getId());
        }
    }

    private void anonimizarUsuario(com.agenda.domain.usuario.Usuario usuario) {
        usuario.setNome("Usuário Removido");
        usuario.setEmail("removido_" + usuario.getId() + "@anonimo.local");
        usuario.setSenhaHash("REMOVIDO");
        usuario.setExcluidoEm(LocalDateTime.now());
    }

    private void anonimizarRegistrosFinanceiros(com.agenda.domain.empresa.Empresa empresa) {
        var empresaId = empresa.getId();
        int total = 0;

        for (var boleto : boletoRepository.findByEmpresa_Id(empresaId)) {
            boleto.setFornecedor("Fornecedor Removido");
            boleto.setObservacoes(null);
            boletoRepository.save(boleto);
            total++;
        }

        for (var pix : pagamentoPixRepository.findByEmpresa_Id(empresaId)) {
            pix.setFornecedor("Fornecedor Removido");
            pix.setChavePix("REMOVIDO");
            pix.setObservacoes(null);
            pagamentoPixRepository.save(pix);
            total++;
        }

        for (var cheque : chequeRepository.findByEmpresa_Id(empresaId)) {
            cheque.setFornecedor("Fornecedor Removido");
            cheque.setNumeroCheque(null);
            cheque.setObservacoes(null);
            chequeRepository.save(cheque);
            total++;
        }

        if (total > 0) {
            log.info("  {} registros financeiros anonimizados", total);
        }
    }
}