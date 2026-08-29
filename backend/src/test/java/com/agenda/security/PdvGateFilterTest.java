package com.agenda.security;

import com.agenda.domain.empresa.EmpresaService;
import com.agenda.shared.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdvGateFilterTest {

    @Mock private EmpresaService empresaService;

    private PdvGateFilter filter;
    private UUID empresaId;

    @BeforeEach
    void setUp() {
        filter = new PdvGateFilter(empresaService, new ObjectMapper().findAndRegisterModules());
        empresaId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private MockHttpServletResponse executar(String metodo, String uri) throws Exception {
        var request = new MockHttpServletRequest(metodo, uri);
        request.setRequestURI(uri);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    /**
     * Bloqueia LEITURA também, ao contrário do gate de assinatura — lá o
     * inadimplente vê os próprios dados e só não escreve. Aqui não há o que
     * ver: o módulo nunca existiu para essa empresa.
     */
    @Test
    void leituraSemModulo_DeveRetornar403() throws Exception {
        TenantContext.setEmpresaId(empresaId);
        when(empresaService.pdvHabilitado(empresaId)).thenReturn(false);

        var response = executar("GET", "/api/produtos");

        assertEquals(403, response.getStatus());
    }

    @Test
    void escritaSemModulo_DeveRetornar403() throws Exception {
        TenantContext.setEmpresaId(empresaId);
        when(empresaService.pdvHabilitado(empresaId)).thenReturn(false);

        assertEquals(403, executar("POST", "/api/produtos").getStatus());
    }

    @Test
    void vendasSemModulo_DeveRetornar403() throws Exception {
        TenantContext.setEmpresaId(empresaId);
        when(empresaService.pdvHabilitado(empresaId)).thenReturn(false);

        assertEquals(403, executar("GET", "/api/vendas/relatorio").getStatus());
    }

    /**
     * 403 e NÃO 402: o 402 faz o frontend redirecionar para /assinar
     * (`client.ts`), e módulo não contratado não é inadimplência — cairia numa
     * tela de pagamento que não resolve nada.
     */
    @Test
    void bloqueio_NaoPodeSer402() throws Exception {
        TenantContext.setEmpresaId(empresaId);
        when(empresaService.pdvHabilitado(empresaId)).thenReturn(false);

        assertNotEquals(402, executar("GET", "/api/produtos").getStatus());
    }

    @Test
    void comModuloLigado_DevePassar() throws Exception {
        TenantContext.setEmpresaId(empresaId);
        when(empresaService.pdvHabilitado(empresaId)).thenReturn(true);

        assertEquals(200, executar("GET", "/api/produtos").getStatus());
    }

    /** O gate é só do PDV: o resto do sistema não passa por esta consulta. */
    @Test
    void rotaForaDoPdv_NaoDeveConsultarOFlag() throws Exception {
        TenantContext.setEmpresaId(empresaId);

        assertEquals(200, executar("GET", "/api/boletos").getStatus());
        verify(empresaService, never()).pdvHabilitado(any());
    }

    /** Sem JWT não há tenant — quem responde 401 é o Spring Security, não este filtro. */
    @Test
    void semTenant_DevePassar() throws Exception {
        assertEquals(200, executar("GET", "/api/produtos").getStatus());
        verify(empresaService, never()).pdvHabilitado(any());
    }
}
