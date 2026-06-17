package com.agenda.domain.boleto;

import com.agenda.domain.arquivo.ArquivoService;
import com.agenda.security.JwtService;
import com.agenda.shared.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BoletoController.class)
class BoletoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BoletoService boletoService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private JpaMetamodelMappingContext jpaMappingContext;

    @MockBean
    private ArquivoService arquivoService;

    @Test
    void listar_DeveRetornar200() throws Exception {
        var dto = new BoletoDTO(UUID.randomUUID(), UUID.randomUUID(), "Loja", "#1e40af",
            "Fornecedor", new BigDecimal("100"), LocalDate.now(), StatusBoleto.PENDENTE,
            null, null, null, null, null, null, null);
        var page = new PageImpl<>(List.of(dto), PageRequest.of(0, 10), 1);

        when(boletoService.listar(any(), any(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/boletos")
                .with(user("admin@teste.com").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void criar_DeveRetornar201() throws Exception {
        var req = new CriarBoletoRequest(UUID.randomUUID(), "Fornecedor",
            new BigDecimal("150"), LocalDate.now().plusDays(30), null, null);
        var dto = new BoletoDTO(UUID.randomUUID(), req.lojaId(), "Loja", "#1e40af",
            req.fornecedor(), req.valor(), req.vencimento(), StatusBoleto.PENDENTE,
            null, null, null, null, null, null, null);

        when(boletoService.criar(any())).thenReturn(dto);

        mockMvc.perform(post("/api/boletos")
                .with(user("operador@teste.com").roles("OPERADOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fornecedor").value("Fornecedor"));
    }

    @Test
    void editar_DeveRetornar200() throws Exception {
        var id = UUID.randomUUID();
        var req = new EditarBoletoRequest(UUID.randomUUID(), "Novo Fornecedor",
            new BigDecimal("200"), LocalDate.now(), "12345678901", null);
        var dto = new BoletoDTO(id, req.lojaId(), "Loja", "#1e40af",
            req.fornecedor(), req.valor(), req.vencimento(), StatusBoleto.PENDENTE,
            null, null, null, null, null, null, null);

        when(boletoService.editar(eq(id), any())).thenReturn(dto);

        mockMvc.perform(put("/api/boletos/{id}", id)
                .with(user("admin@teste.com").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fornecedor").value("Novo Fornecedor"));
    }

    @Test
    void excluir_DeveRetornar204() throws Exception {
        var id = UUID.randomUUID();

        mockMvc.perform(delete("/api/boletos/{id}", id)
                .with(user("admin@teste.com").roles("ADMIN"))
                .with(csrf()))
            .andExpect(status().isNoContent());
    }

    @Test
    void mudarStatus_DeveRetornar200() throws Exception {
        var id = UUID.randomUUID();
        var req = new MudarStatusRequest(StatusBoleto.PAGO);
        var dto = new BoletoDTO(id, UUID.randomUUID(), "Loja", "#1e40af",
            "Fornecedor", new BigDecimal("100"), LocalDate.now(), StatusBoleto.PAGO,
            null, null, null, null, null, null, null);

        when(boletoService.mudarStatus(eq(id), eq(StatusBoleto.PAGO))).thenReturn(dto);

        mockMvc.perform(patch("/api/boletos/{id}/status", id)
                .with(user("operador@teste.com").roles("OPERADOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAGO"));
    }

    @Test
    void editar_DeveRetornar404QuandoNaoEncontrado() throws Exception {
        var id = UUID.randomUUID();
        var req = new EditarBoletoRequest(UUID.randomUUID(), "X",
            new BigDecimal("1"), LocalDate.now(), null, null);

        when(boletoService.editar(eq(id), any())).thenThrow(new NotFoundException("Boleto não encontrado"));

        mockMvc.perform(put("/api/boletos/{id}", id)
                .with(user("admin@teste.com").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isNotFound());
    }

    @Test
    void criar_DeveRetornar422QuandoDadosInvalidos() throws Exception {
        var req = new CriarBoletoRequest(null, "",
            null, null, null, null);

        mockMvc.perform(post("/api/boletos")
                .with(user("admin@teste.com").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isUnprocessableEntity());
    }
}
