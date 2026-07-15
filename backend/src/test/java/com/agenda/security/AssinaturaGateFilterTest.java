package com.agenda.security;

import com.agenda.domain.assinatura.AssinaturaService;
import com.agenda.shared.TenantContext;
import com.agenda.shared.UserContext;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssinaturaGateFilterTest {

    @Mock private AssinaturaService assinaturaService;

    private AssinaturaGateFilter filter;
    private UUID empresaId;

    @BeforeEach
    void setUp() {
        filter = new AssinaturaGateFilter(assinaturaService, new ObjectMapper().findAndRegisterModules());
        empresaId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        UserContext.clear();
    }

    private MockHttpServletResponse executar(String metodo, String uri) throws Exception {
        var request = new MockHttpServletRequest(metodo, uri);
        request.setRequestURI(uri);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void escritaComAssinaturaBloqueada_DeveRetornar402() throws Exception {
        TenantContext.setEmpresaId(empresaId);
        when(assinaturaService.podeAcessar(empresaId)).thenReturn(false);

        var response = executar("POST", "/api/boletos");

        assertEquals(402, response.getStatus());
        assertTrue(response.getContentAsString().contains("suspenso"));
    }

    @Test
    void leituraComAssinaturaBloqueada_DevePassar() throws Exception {
        TenantContext.setEmpresaId(empresaId);

        var response = executar("GET", "/api/boletos");

        assertEquals(200, response.getStatus());
        verify(assinaturaService, never()).podeAcessar(any());
    }

    @Test
    void escritaComAssinaturaAcessavel_DevePassar() throws Exception {
        TenantContext.setEmpresaId(empresaId);
        when(assinaturaService.podeAcessar(empresaId)).thenReturn(true);

        var response = executar("POST", "/api/boletos");

        assertEquals(200, response.getStatus());
    }

    @Test
    void escritaEmAuth_DevePassarSemChecarAssinatura() throws Exception {
        TenantContext.setEmpresaId(empresaId);

        var response = executar("POST", "/api/auth/refresh");

        assertEquals(200, response.getStatus());
        verify(assinaturaService, never()).podeAcessar(any());
    }

    @Test
    void escritaEmLgpd_DevePassarSemChecarAssinatura() throws Exception {
        TenantContext.setEmpresaId(empresaId);

        var response = executar("POST", "/api/lgpd/solicitar-exclusao");

        assertEquals(200, response.getStatus());
        verify(assinaturaService, never()).podeAcessar(any());
    }

    @Test
    void escritaDeMaster_DevePassarSemChecarAssinatura() throws Exception {
        TenantContext.setEmpresaId(empresaId);
        UserContext.set(UUID.randomUUID(), "Master", "MASTER");

        var response = executar("PUT", "/api/assinaturas/" + UUID.randomUUID());

        assertEquals(200, response.getStatus());
        verify(assinaturaService, never()).podeAcessar(any());
    }

    @Test
    void escritaSemTenant_DevePassar() throws Exception {
        var response = executar("POST", "/api/boletos");

        assertEquals(200, response.getStatus());
        verify(assinaturaService, never()).podeAcessar(any());
    }

    @Test
    void escritaForaDaApi_DevePassarSemChecarAssinatura() throws Exception {
        TenantContext.setEmpresaId(empresaId);

        var response = executar("POST", "/webhook/whatsapp");

        assertEquals(200, response.getStatus());
        verify(assinaturaService, never()).podeAcessar(any());
    }
}
