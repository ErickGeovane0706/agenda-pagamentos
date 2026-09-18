package com.agenda.domain.venda;

import com.agenda.domain.assinatura.AssinaturaService;
import com.agenda.domain.empresa.EmpresaService;
import com.agenda.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VendaController.class)
class VendaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VendaService vendaService;

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

    private CriarVendaRequest pedido() {
        return new CriarVendaRequest(UUID.randomUUID(), "Maria",
            List.of(new CriarVendaRequest.Item(UUID.randomUUID(), new BigDecimal("3.000"), null)));
    }

    private VendaDTO dto() {
        return new VendaDTO(UUID.randomUUID(), UUID.randomUUID(), "Maria",
            new BigDecimal("24.00"), new BigDecimal("9.00"), new BigDecimal("15.00"),
            StatusVenda.CONCLUIDA, LocalDateTime.now(), List.of());
    }

    @Test
    void criar_DeveRetornar200() throws Exception {
        when(vendaService.criar(any())).thenReturn(dto());

        mockMvc.perform(post("/api/vendas")
                .with(user("operador@teste.com").roles("OPERADOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pedido())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lucro").value(15.00));
    }

    /**
     * O 409 do plano, provado pela ponta HTTP: o front precisa distinguir
     * "não tem gelo agora" de "seu formulário está errado" (400) e de
     * "pague a assinatura" (402).
     */
    @Test
    void criar_SemEstoque_DeveRetornar409() throws Exception {
        when(vendaService.criar(any()))
            .thenThrow(new EstoqueInsuficienteException("Estoque insuficiente de Gelo 5kg (disponível: 2.000)"));

        mockMvc.perform(post("/api/vendas")
                .with(user("operador@teste.com").roles("OPERADOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pedido())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("Gelo 5kg")));
    }

    /** Venda sem item nenhum não é venda. */
    @Test
    void criar_SemItens_DeveRetornar422() throws Exception {
        var vazio = new CriarVendaRequest(UUID.randomUUID(), "Maria", List.of());

        mockMvc.perform(post("/api/vendas")
                .with(user("admin@teste.com").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(vazio)))
            .andExpect(status().isUnprocessableEntity());
    }

    /** Quantidade zero é recusada — o CHECK do banco exige > 0. */
    @Test
    void criar_ComQuantidadeZero_DeveRetornar422() throws Exception {
        var req = new CriarVendaRequest(UUID.randomUUID(), "Maria",
            List.of(new CriarVendaRequest.Item(UUID.randomUUID(), BigDecimal.ZERO, null)));

        mockMvc.perform(post("/api/vendas")
                .with(user("admin@teste.com").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cancelar_DeveRetornar204() throws Exception {
        mockMvc.perform(post("/api/vendas/{id}/cancelar", UUID.randomUUID())
                .with(user("admin@teste.com").roles("ADMIN"))
                .with(csrf()))
            .andExpect(status().isNoContent());
    }

    /** Não existe editar venda: PUT não é rota. */
    @Test
    void editar_NaoDeveExistir() throws Exception {
        mockMvc.perform(put("/api/vendas/{id}", UUID.randomUUID())
                .with(user("admin@teste.com").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void listar_DeveRetornar200() throws Exception {
        when(vendaService.listar(any(), any())).thenReturn(List.of(dto()));

        mockMvc.perform(get("/api/vendas")
                .param("lojaId", UUID.randomUUID().toString())
                .with(user("viewer@teste.com").roles("VIEWER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }
}
