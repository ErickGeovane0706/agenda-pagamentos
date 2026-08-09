package com.agenda.security;

import com.agenda.domain.assinatura.AssinaturaService;
import com.agenda.domain.auth.*;
import com.agenda.domain.usuario.EtapaOnboarding;
import com.agenda.domain.usuario.PerfilUsuario;
import com.agenda.domain.usuario.UsuarioDTO;
import com.agenda.config.CorsConfig;
import com.agenda.config.SecurityConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre o JwtAuthFilter pela borda HTTP, não por unidade.
 *
 * A escolha é deliberada: o defeito que este teste guarda não está em o filtro
 * lançar exceção — está em ela ESCAPAR do filtro. Exceção lançada em filtro não
 * passa pelo @ControllerAdvice, que só cobre controller, então o
 * RestExceptionHandler nunca converte para 401 e o container devolve 500. Um
 * teste unitário com mocks veria o filtro lançar e poderia até "passar" com um
 * assertThrows, sem nunca tocar na parte que quebrou em produção.
 */
/*
 * SecurityConfig e JwtAuthFilter entram explicitamente porque o @WebMvcTest não
 * carrega o @Configuration de segurança do projeto — sem eles vale a proteção
 * padrão do Spring Boot, que devolve 401 com Basic realm em /api/auth/login e
 * nunca deixa a requisição alcançar o filtro que este teste cobre.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthFilter.class})
class JwtAuthFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private AuthService authService;
    @MockBean private SenhaResetService senhaResetService;
    @MockBean private RegistroService registroService;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private JpaMetamodelMappingContext jpaMappingContext;
    @MockBean private AssinaturaService assinaturaService;

    /**
     * Regressão de 08/08: depois que 11 usuários foram apagados do banco, quem
     * tinha cookie "jwt" ainda dentro da validade parou de conseguir logar.
     *
     * O cookie tem path=/api, então acompanha o próprio POST de login. O filtro
     * o lê ANTES do login acontecer, extrai o email e procura o usuário — que
     * não existe mais. UsernameNotFoundException não é JwtException nem
     * IllegalArgumentException (docs do Spring Security: ela estende
     * AuthenticationException), então escapa dos dois catch do filtro.
     *
     * O cookie velho não pode ter voto nenhum sobre um login novo: quem manda
     * email e senha corretos entra, tendo cookie podre na máquina ou não.
     */
    @Test
    void loginComCookieDeUsuarioApagado_DeveEntrar_NaoDarErro500() throws Exception {
        when(jwtService.extractEmail("cookie.de.conta.apagada"))
                .thenReturn("apagado@exemplo.com");
        when(userDetailsService.loadUserByUsername("apagado@exemplo.com"))
                .thenThrow(new UsernameNotFoundException("Usuário não encontrado"));

        var usuario = new UsuarioDTO(UUID.randomUUID(), "Fulano", "fulano@test.com",
                PerfilUsuario.ADMIN, UUID.randomUUID(), "Empresa", EtapaOnboarding.CONCLUIDO);
        when(authService.login(any())).thenReturn(
                new AuthLoginResult("access-token", "refresh-token", usuario));

        mockMvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("jwt", "cookie.de.conta.apagada"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"fulano@test.com\",\"senha\":\"senha123\"}"))
                .andExpect(status().isOk());
    }
}
