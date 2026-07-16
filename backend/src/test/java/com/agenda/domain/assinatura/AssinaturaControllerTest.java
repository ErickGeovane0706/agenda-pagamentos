package com.agenda.domain.assinatura;

import com.agenda.security.JwtService;
import com.agenda.shared.TenantContext;
import com.agenda.shared.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O papel vem do Spring Security (@PreAuthorize) e o tenant do TenantContext,
 * que em produção é populado pelo JwtAuthFilter — ausente no @WebMvcTest, por
 * isso os testes o preenchem na mão.
 * <p>
 * O @Import não é decoração: o @WebMvcTest não carrega o SecurityConfig da
 * aplicação (onde vive o @EnableMethodSecurity), então sem ele o @PreAuthorize
 * seria ignorado e os testes de papel passariam sem testar nada.
 */
@WebMvcTest(AssinaturaController.class)
@Import(AssinaturaControllerTest.MethodSecurityHabilitada.class)
class AssinaturaControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityHabilitada {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AssinaturaService assinaturaService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JpaMetamodelMappingContext jpaMappingContext;

    @AfterEach
    void limparContexto() {
        UserContext.clear();
        TenantContext.clear();
    }

    private void autenticadoComo(String perfil, UUID empresaId) {
        UserContext.set(UUID.randomUUID(), "fulano", perfil);
        TenantContext.setEmpresaId(empresaId);
    }

    @Test
    void assinar_MasterRecebe200ComUrlDePagamento() throws Exception {
        var id = UUID.randomUUID();
        autenticadoComo("MASTER", null);
        when(assinaturaService.assinar(id))
            .thenReturn(new AssinaturaCheckoutDTO("sub_1", "https://asaas.com/i/xyz"));

        mockMvc.perform(post("/api/assinaturas/{empresaId}/assinar", id)
                .with(user("master@teste.com").roles("MASTER"))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subscriptionId").value("sub_1"))
            .andExpect(jsonPath("$.urlPagamento").value("https://asaas.com/i/xyz"));
    }

    @Test
    void cancelar_MasterRecebe204EChamaService() throws Exception {
        var id = UUID.randomUUID();
        autenticadoComo("MASTER", null);

        mockMvc.perform(delete("/api/assinaturas/{empresaId}/assinar", id)
                .with(user("master@teste.com").roles("MASTER"))
                .with(csrf()))
            .andExpect(status().isNoContent());

        verify(assinaturaService).cancelarAssinatura(id);
    }

    @Test
    void assinar_AdminDaPropriaEmpresa_DevePermitir() throws Exception {
        var id = UUID.randomUUID();
        autenticadoComo("ADMIN", id);
        when(assinaturaService.assinar(id))
            .thenReturn(new AssinaturaCheckoutDTO("sub_1", "https://asaas.com/i/xyz"));

        mockMvc.perform(post("/api/assinaturas/{empresaId}/assinar", id)
                .with(user("admin@teste.com").roles("ADMIN"))
                .with(csrf()))
            .andExpect(status().isOk());
    }

    /**
     * O IDOR que o self-service abriria: o papel ADMIN sozinho não pode bastar,
     * senão troca-se o UUID da URL e dispara-se cobrança na empresa alheia.
     */
    @Test
    void assinar_AdminDeOutraEmpresa_DeveNegarSemChamarOGateway() throws Exception {
        var outraEmpresa = UUID.randomUUID();
        autenticadoComo("ADMIN", UUID.randomUUID());

        mockMvc.perform(post("/api/assinaturas/{empresaId}/assinar", outraEmpresa)
                .with(user("admin@teste.com").roles("ADMIN"))
                .with(csrf()))
            .andExpect(status().isForbidden());

        verify(assinaturaService, never()).assinar(any());
    }

    /** O pior caso do IDOR: cancelar a assinatura de outra empresa. */
    @Test
    void cancelar_AdminDeOutraEmpresa_DeveNegar() throws Exception {
        var outraEmpresa = UUID.randomUUID();
        autenticadoComo("ADMIN", UUID.randomUUID());

        mockMvc.perform(delete("/api/assinaturas/{empresaId}/assinar", outraEmpresa)
                .with(user("admin@teste.com").roles("ADMIN"))
                .with(csrf()))
            .andExpect(status().isForbidden());

        verify(assinaturaService, never()).cancelarAssinatura(any());
    }

    /** Cobrança é decisão de gestor: operador não contrata nem cancela. */
    @Test
    void assinar_Operador_DeveNegarPeloPapel() throws Exception {
        var id = UUID.randomUUID();
        autenticadoComo("OPERADOR", id);

        mockMvc.perform(post("/api/assinaturas/{empresaId}/assinar", id)
                .with(user("op@teste.com").roles("OPERADOR"))
                .with(csrf()))
            .andExpect(status().isForbidden());

        verify(assinaturaService, never()).assinar(any());
    }

    /** A listagem do painel continua exclusiva do MASTER. */
    @Test
    void listar_Admin_DeveNegar() throws Exception {
        autenticadoComo("ADMIN", UUID.randomUUID());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/assinaturas")
                .with(user("admin@teste.com").roles("ADMIN")))
            .andExpect(status().isForbidden());
    }
}
