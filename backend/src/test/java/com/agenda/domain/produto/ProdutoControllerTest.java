package com.agenda.domain.produto;

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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProdutoController.class)
class ProdutoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProdutoService produtoService;

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

    private ProdutoDTO dto(String nome) {
        return new ProdutoDTO(UUID.randomUUID(), UUID.randomUUID(), nome,
            new BigDecimal("40.000"), new BigDecimal("3.00"), new BigDecimal("8.00"), true);
    }

    @Test
    void listar_DeveRetornar200() throws Exception {
        when(produtoService.listar(any())).thenReturn(List.of(dto("Gelo 5kg")));

        mockMvc.perform(get("/api/produtos")
                .param("lojaId", UUID.randomUUID().toString())
                .with(user("admin@teste.com").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    /** Estoque é por loja (§4.1): sem {@code lojaId} a requisição não faz sentido. */
    @Test
    void listar_DeveRetornar400SemLojaId() throws Exception {
        mockMvc.perform(get("/api/produtos")
                .with(user("admin@teste.com").roles("ADMIN")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void criar_DeveRetornar200() throws Exception {
        var req = new CriarProdutoRequest(UUID.randomUUID(), "Gelo 5kg",
            new BigDecimal("40.000"), new BigDecimal("3.00"), new BigDecimal("8.00"));
        when(produtoService.criar(any())).thenReturn(dto("Gelo 5kg"));

        mockMvc.perform(post("/api/produtos")
                .with(user("operador@teste.com").roles("OPERADOR"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Gelo 5kg"));
    }

    /**
     * Verificação 4 da Fase 2 do plano: preço negativo é recusado. Fica no
     * controller porque quem barra é o Bean Validation do
     * {@link CriarProdutoRequest} — o service nunca chega a ser chamado.
     */
    @Test
    void criar_DeveRetornar422ComPrecoNegativo() throws Exception {
        var req = new CriarProdutoRequest(UUID.randomUUID(), "Gelo 5kg",
            new BigDecimal("40.000"), new BigDecimal("-3.00"), new BigDecimal("8.00"));

        mockMvc.perform(post("/api/produtos")
                .with(user("admin@teste.com").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isUnprocessableEntity());
    }

    /** Quantidade negativa também: é o {@code ck_produto_qtd_nao_negativa} dito antes do banco. */
    @Test
    void criar_DeveRetornar422ComQuantidadeNegativa() throws Exception {
        var req = new CriarProdutoRequest(UUID.randomUUID(), "Gelo 5kg",
            new BigDecimal("-1.000"), new BigDecimal("3.00"), new BigDecimal("8.00"));

        mockMvc.perform(post("/api/produtos")
                .with(user("admin@teste.com").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isUnprocessableEntity());
    }

    /** Estoque e preço zerados são legítimos: cadastra hoje, recebe a carga amanhã. */
    @Test
    void criar_DeveAceitarQuantidadeEPrecoZero() throws Exception {
        var req = new CriarProdutoRequest(UUID.randomUUID(), "Brinde",
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        when(produtoService.criar(any())).thenReturn(dto("Brinde"));

        mockMvc.perform(post("/api/produtos")
                .with(user("admin@teste.com").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());
    }

    @Test
    void desativar_DeveRetornar204() throws Exception {
        mockMvc.perform(delete("/api/produtos/{id}", UUID.randomUUID())
                .with(user("admin@teste.com").roles("ADMIN"))
                .with(csrf()))
            .andExpect(status().isNoContent());
    }
}
