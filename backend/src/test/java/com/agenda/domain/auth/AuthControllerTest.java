package com.agenda.domain.auth;

import com.agenda.domain.assinatura.AssinaturaService;
import com.agenda.domain.empresa.EmpresaService;
import com.agenda.domain.usuario.EtapaOnboarding;
import com.agenda.domain.usuario.PerfilUsuario;
import com.agenda.domain.usuario.UsuarioDTO;
import com.agenda.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@TestPropertySource(properties = "security.cookie-secure=true")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private AuthService authService;
    @MockBean private SenhaResetService senhaResetService;
    @MockBean private RegistroService registroService;
    @MockBean private JwtService jwtService;
    @MockBean private JpaMetamodelMappingContext jpaMappingContext;
    // Exigido pelo AssinaturaGateFilter (@Component Filter, escaneado pelo @WebMvcTest).
    @MockBean private AssinaturaService assinaturaService;
    /** Não usado aqui, mas o PdvGateFilter entra no contexto e exige o bean. */
    @MockBean private EmpresaService empresaService;

    @Test
    void login_ComCookieSecureLigado_DeveEmitirCookiesComFlagSecure() throws Exception {
        var usuario = new UsuarioDTO(UUID.randomUUID(), "Fulano", "fulano@test.com",
                PerfilUsuario.ADMIN, UUID.randomUUID(), "Empresa", EtapaOnboarding.CONCLUIDO);
        when(authService.login(any())).thenReturn(
                new AuthLoginResult("access-token", "refresh-token", usuario));

        var result = mockMvc.perform(post("/api/auth/login")
                        .with(user("anon")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"fulano@test.com\",\"senha\":\"senha123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        List<String> cookies = result.getResponse().getHeaders("Set-Cookie");
        assertEquals(2, cookies.size(), "deve emitir cookie jwt e refresh_token");
        assertTrue(cookies.stream().allMatch(c -> c.contains("Secure")),
                "todos os cookies de auth devem ter a flag Secure");
        assertTrue(cookies.stream().anyMatch(c -> c.startsWith("jwt=")));
        assertTrue(cookies.stream().anyMatch(c -> c.startsWith("refresh_token=")));
    }
}
