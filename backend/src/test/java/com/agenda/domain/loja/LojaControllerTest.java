package com.agenda.domain.loja;

import com.agenda.domain.assinatura.AssinaturaService;
import com.agenda.domain.empresa.EmpresaService;
import com.agenda.security.JwtService;
import com.agenda.shared.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LojaController.class)
class LojaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LojaService lojaService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JpaMetamodelMappingContext jpaMappingContext;

    // Exigido pelo AssinaturaGateFilter (@Component Filter, escaneado pelo @WebMvcTest).
    @MockBean
    private AssinaturaService assinaturaService;

    // Exigido pelo PdvGateFilter (@Component Filter, escaneado pelo @WebMvcTest).
    @MockBean
    private EmpresaService empresaService;

    @Test
    void listar_DeveRetornar200() throws Exception {
        when(lojaService.listar()).thenReturn(List.of(
            new LojaDTO(UUID.randomUUID(), "Loja A", null, null, "#1e40af", true)
        ));

        mockMvc.perform(get("/api/lojas")
                .with(user("admin@teste.com").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void criar_DeveRetornar200() throws Exception {
        var req = new CriarLojaRequest("Loja Nova", null, null, "#065f46");
        var dto = new LojaDTO(UUID.randomUUID(), "Loja Nova", null, null, "#065f46", true);

        when(lojaService.criar(any())).thenReturn(dto);

        mockMvc.perform(post("/api/lojas")
                .with(user("admin@teste.com").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Loja Nova"));
    }

    @Test
    void buscar_DeveRetornar200() throws Exception {
        var id = UUID.randomUUID();
        var dto = new LojaDTO(id, "Loja X", null, null, "#1e40af", true);

        when(lojaService.buscar(id)).thenReturn(dto);

        mockMvc.perform(get("/api/lojas/{id}", id)
                .with(user("viewer@teste.com").roles("VIEWER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Loja X"));
    }

    @Test
    void buscar_DeveRetornar404QuandoNaoEncontrada() throws Exception {
        var id = UUID.randomUUID();
        when(lojaService.buscar(id)).thenThrow(new NotFoundException("Loja não encontrada"));

        mockMvc.perform(get("/api/lojas/{id}", id)
                .with(user("admin@teste.com").roles("ADMIN")))
            .andExpect(status().isNotFound());
    }

    @Test
    void editar_DeveRetornar200() throws Exception {
        var id = UUID.randomUUID();
        var req = new EditarLojaRequest("Editada", null, null, "#b91c1c");
        var dto = new LojaDTO(id, "Editada", null, null, "#b91c1c", true);

        when(lojaService.editar(eq(id), any())).thenReturn(dto);

        mockMvc.perform(put("/api/lojas/{id}", id)
                .with(user("admin@teste.com").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Editada"));
    }

    @Test
    void excluir_DeveRetornar204() throws Exception {
        mockMvc.perform(delete("/api/lojas/{id}", UUID.randomUUID())
                .with(user("admin@teste.com").roles("ADMIN"))
                .with(csrf()))
            .andExpect(status().isNoContent());
    }

}
