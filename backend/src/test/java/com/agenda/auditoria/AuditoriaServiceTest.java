package com.agenda.auditoria;

import com.agenda.shared.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock private AuditoriaRepository auditoriaRepository;
    private AuditoriaService auditoriaService;
    private UUID empresaId;

    @BeforeEach
    void setUp() {
        auditoriaService = new AuditoriaService(auditoriaRepository);
        empresaId = UUID.randomUUID();
        TenantContext.setEmpresaId(empresaId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void registrar_DeveSalvarRegistro() {
        var entidadeId = UUID.randomUUID();
        auditoriaService.registrar("CRIAR", "BOLETO", entidadeId, "Fornecedor: Teste");
        verify(auditoriaRepository).save(any(Auditoria.class));
    }
}