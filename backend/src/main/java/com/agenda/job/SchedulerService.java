package com.agenda.job;

import com.agenda.domain.empresa.EmpresaRepository;
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

    @Transactional
    @Scheduled(cron = "0 0 3 * * *")
    public void processarExclusoes() {
        log.info("Processando solicitações de exclusão...");
        var empresas = empresaRepository.findBySolicitouExclusaoTrueAndExcluidoEmIsNull();

        for (var empresa : empresas) {
            var usuarios = usuarioRepository.findByEmpresaId(empresa.getId());
            for (var usuario : usuarios) {
                usuario.setNome("Usuário Removido");
                usuario.setEmail("removido_" + usuario.getId() + "@anonimo.local");
                usuario.setSenhaHash("REMOVIDO");
                usuario.setExcluidoEm(LocalDateTime.now());
                usuarioRepository.save(usuario);
            }
            empresa.setExcluidoEm(LocalDateTime.now());
            empresaRepository.save(empresa);
            log.info("Empresa {} anonimizada com {} usuários", empresa.getId(), usuarios.size());
        }

        if (empresas.isEmpty()) {
            log.info("Nenhuma exclusão pendente.");
        }
    }
}