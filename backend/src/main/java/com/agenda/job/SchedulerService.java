package com.agenda.job;

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

@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulerService {

    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final BoletoRepository boletoRepository;
    private final PagamentoPixRepository pagamentoPixRepository;
    private final ChequeRepository chequeRepository;

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

        if (empresas.isEmpty()) {
            log.info("Nenhuma exclusão pendente.");
        }
    }

    private void anonimizarUsuarios(com.agenda.domain.empresa.Empresa empresa) {
        var usuarios = usuarioRepository.findByEmpresaId(empresa.getId());
        for (var usuario : usuarios) {
            usuario.setNome("Usuário Removido");
            usuario.setEmail("removido_" + usuario.getId() + "@anonimo.local");
            usuario.setSenhaHash("REMOVIDO");
            usuario.setExcluidoEm(LocalDateTime.now());
            usuarioRepository.save(usuario);
        }
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