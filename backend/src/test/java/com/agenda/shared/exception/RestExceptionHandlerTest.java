package com.agenda.shared.exception;

import com.agenda.domain.assinatura.AssinaturaService;
import com.agenda.domain.empresa.EmpresaService;
import com.agenda.domain.loja.LojaController;
import com.agenda.domain.loja.LojaService;
import com.agenda.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Entrada malformada deve virar 4xx, não 500.
 * Sem estes handlers as exceções do Spring MVC caem no
 * @ExceptionHandler(Exception.class) genérico e viram 500
 * (achado do pentest ZAP de 20/07/2026, plugin 90022).
 * Usa LojaController por ter rota com @PathVariable UUID.
 */
@WebMvcTest(LojaController.class)
class RestExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private LojaService lojaService;
    @MockBean private JwtService jwtService;
    @MockBean private JpaMetamodelMappingContext jpaMappingContext;
    // Exigido pelo AssinaturaGateFilter (@Component Filter, escaneado pelo @WebMvcTest).
    @MockBean private AssinaturaService assinaturaService;
    /** Não usado aqui, mas o PdvGateFilter entra no contexto e exige o bean. */
    @MockBean private EmpresaService empresaService;

    @Test
    void metodoNaoSuportado_DeveRetornar405() throws Exception {
        mockMvc.perform(patch("/api/lojas")
                .with(user("admin@teste.com").roles("ADMIN"))
                .with(csrf()))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void jsonMalformado_DeveRetornar400() throws Exception {
        mockMvc.perform(post("/api/lojas")
                .with(user("admin@teste.com").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{isso nao e json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void corpoAusente_DeveRetornar400() throws Exception {
        mockMvc.perform(post("/api/lojas")
                .with(user("admin@teste.com").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
    }

    @Test
    void contentTypeNaoSuportado_DeveRetornar415() throws Exception {
        mockMvc.perform(post("/api/lojas")
                .with(user("admin@teste.com").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.TEXT_PLAIN)
                .content("qualquer coisa"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void uuidInvalidoNoPath_DeveRetornar400() throws Exception {
        mockMvc.perform(get("/api/lojas/{id}", "nao-e-uuid")
                .with(user("admin@teste.com").roles("ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rotaInexistente_DeveRetornar404() throws Exception {
        mockMvc.perform(get("/api/lojas/nao/existe/essa/rota")
                .with(user("admin@teste.com").roles("ADMIN")))
            .andExpect(status().isNotFound());
    }

    @Test
    void erroInterno_DeveContinuar500SemVazarDetalhe() throws Exception {
        org.mockito.Mockito.when(lojaService.listar())
            .thenThrow(new RuntimeException("stack trace secreto"));

        mockMvc.perform(get("/api/lojas")
                .with(user("admin@teste.com").roles("ADMIN")))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.mensagem").value("Erro interno do servidor"))
            .andExpect(jsonPath("$.detalhe").doesNotExist());
    }
}
