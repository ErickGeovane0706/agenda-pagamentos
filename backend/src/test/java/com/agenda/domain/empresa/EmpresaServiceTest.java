package com.agenda.domain.empresa;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.domain.assinatura.AssinaturaService;
import com.agenda.domain.banco.BancoRepository;
import com.agenda.domain.loja.LojaRepository;
import com.agenda.domain.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmpresaServiceTest {

    @Mock private EmpresaRepository empresaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private LojaRepository lojaRepository;
    @Mock private BancoRepository bancoRepository;
    @Mock private AssinaturaService assinaturaService;
    @Mock private AuditoriaService auditoriaService;

    private EmpresaService empresaService;

    @BeforeEach
    void setUp() {
        empresaService = new EmpresaService(
            empresaRepository, usuarioRepository, lojaRepository, bancoRepository, assinaturaService,
            auditoriaService);
    }

    @Test
    void criar_DeveSalvarEmpresaECriarAssinaturaTrial() {
        when(empresaRepository.save(any())).thenAnswer(i -> {
            var e = i.getArgument(0, Empresa.class);
            e.setId(UUID.randomUUID());
            return e;
        });

        var result = empresaService.criar(new CriarEmpresaRequest("Empresa Nova"));

        assertEquals("Empresa Nova", result.nome());
        verify(assinaturaService).criarTrial(argThat(e -> "Empresa Nova".equals(e.getNome())));
    }
}
