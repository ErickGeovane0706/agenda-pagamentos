package com.agenda.domain.assinatura;

import com.agenda.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AssinaturaController.class)
class AssinaturaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AssinaturaService assinaturaService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JpaMetamodelMappingContext jpaMappingContext;

    @Test
    void assinar_MasterRecebe200ComUrlDePagamento() throws Exception {
        var id = UUID.randomUUID();
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

        mockMvc.perform(delete("/api/assinaturas/{empresaId}/assinar", id)
                .with(user("master@teste.com").roles("MASTER"))
                .with(csrf()))
            .andExpect(status().isNoContent());

        verify(assinaturaService).cancelarAssinatura(id);
    }
}
