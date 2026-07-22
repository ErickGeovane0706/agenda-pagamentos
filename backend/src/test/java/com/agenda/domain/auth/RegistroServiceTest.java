package com.agenda.domain.auth;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.domain.empresa.EmpresaDTO;
import com.agenda.domain.empresa.EmpresaService;
import com.agenda.domain.loja.CriarLojaRequest;
import com.agenda.domain.loja.LojaService;
import com.agenda.domain.usuario.EtapaOnboarding;
import com.agenda.domain.usuario.PasswordValidator;
import com.agenda.domain.usuario.PerfilUsuario;
import com.agenda.domain.usuario.Usuario;
import com.agenda.domain.usuario.UsuarioDTO;
import com.agenda.domain.usuario.UsuarioRepository;
import com.agenda.domain.usuario.UsuarioService;
import com.agenda.email.EmailService;
import com.agenda.security.JwtService;
import com.agenda.security.RateLimiterService;
import com.agenda.security.RefreshTokenService;
import com.agenda.security.TurnstileValidator;
import com.agenda.shared.TenantContext;
import com.agenda.shared.exception.TooManyRequestsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Cobre o "critério de pronto" de segurança do PLANO-REGISTRO.md para o
 * cadastro público: anti-enumeração (inclusive por tempo), perfil sempre ADMIN,
 * token de uso único com erro genérico, rate limit nas duas dimensões, honeypot
 * e a corrida de dois pendentes do mesmo email.
 * <p>
 * Cobre também a restrição arquitetural que derrubou a primeira versão deste
 * fluxo: a auditoria roda em {@code REQUIRES_NEW} e não enxerga a empresa ainda
 * não commitada, então nada pode ser auditado durante a transação.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistroServiceTest {

    @Mock private RegistroPendenteRepository pendenteRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private EmpresaService empresaService;
    @Mock private LojaService lojaService;
    @Mock private UsuarioService usuarioService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TurnstileValidator turnstileValidator;
    @Mock private EmailService emailService;
    @Mock private AuditoriaService auditoriaService;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;

    private RegistroService service;

    private static final UUID EMPRESA_ID = UUID.randomUUID();
    private static final UUID USUARIO_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RegistroService(pendenteRepository, usuarioRepository, empresaService,
                lojaService, usuarioService, new PasswordValidator(), passwordEncoder,
                new RateLimiterService(), turnstileValidator, emailService, auditoriaService,
                jwtService, refreshTokenService);
        ReflectionTestUtils.setField(service, "ttlHoras", 48);
        ReflectionTestUtils.setField(service, "frontendUrl", "https://app.teste");

        when(turnstileValidator.valido(any(), any())).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hash-falso");
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // confirmar() agenda a auditoria para depois do commit; sem sincronização
        // ativa, o registerSynchronization estouraria fora de uma transação real.
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
        TenantContext.clear();
    }

    private RegistroDTO.Solicitacao pedido(String email) {
        return new RegistroDTO.Solicitacao("Mercado do João", "João Batista", email,
                "SenhaBoa123", true, "token-turnstile", null);
    }

    private RegistroPendente pendente(LocalDateTime expiraEm) {
        return RegistroPendente.builder()
                .id(UUID.randomUUID())
                .nomeNegocio("Mercado do João")
                .nomeUsuario("João Batista")
                .email("joao@test.com")
                .senhaHash("$2a$12$hash-falso")
                .tokenHash("x")
                .expiraEm(expiraEm)
                .build();
    }

    /** Prepara os mocks do caminho feliz de {@code confirmar}. */
    private UsuarioDTO prepararProvisionamento() {
        var dto = new UsuarioDTO(USUARIO_ID, "João Batista", "joao@test.com",
                PerfilUsuario.ADMIN, EMPRESA_ID, "Mercado do João");
        when(pendenteRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(pendente(LocalDateTime.now().plusHours(1))));
        when(empresaService.criar(any())).thenReturn(new EmpresaDTO(EMPRESA_ID, "Mercado do João", true));
        when(usuarioService.criarAdminDoRegistro(any(), any(), any(), any(), any())).thenReturn(dto);
        when(usuarioRepository.getReferenceById(USUARIO_ID)).thenReturn(new Usuario());
        when(jwtService.generateToken(any())).thenReturn("jwt-falso");
        var refresh = new RefreshToken();
        refresh.setToken("refresh-falso");
        when(refreshTokenService.create(any())).thenReturn(refresh);
        return dto;
    }

    // ─── Anti-enumeração ──────────────────────────────────────────────────────

    @Test
    void solicitar_EmailLivre_CriaPendenteEEnviaLink() {
        service.solicitar(pedido("novo@test.com"), "10.0.0.1");

        verify(pendenteRepository).save(any(RegistroPendente.class));
        verify(emailService).enviar(eq("novo@test.com"), contains("Confirme"), anyString());
    }

    @Test
    void solicitar_EmailJaCadastrado_NaoCriaPendenteMasEnviaAvisoENaoLanca() {
        when(usuarioRepository.findByEmail("ocupado@test.com"))
                .thenReturn(Optional.of(new Usuario()));

        assertDoesNotThrow(() -> service.solicitar(pedido("ocupado@test.com"), "10.0.0.1"));

        verify(pendenteRepository, never()).save(any());
        // O titular da caixa é avisado — para ele a informação é útil e não
        // revela nada que ele já não saiba.
        verify(emailService).enviar(eq("ocupado@test.com"), anyString(), contains("já tem uma conta"));
    }

    /**
     * O BCrypt custa ~250ms. Se rodasse só no caminho do email livre, a diferença
     * de latência entre os dois caminhos seria um oráculo de enumeração tão bom
     * quanto uma mensagem de erro diferente. Por isso ele vem ANTES da consulta —
     * e a ordem é o que este teste protege.
     */
    @Test
    void solicitar_HasheiaAntesDeConsultarOEmail_ParaNaoVazarPorTempo() {
        service.solicitar(pedido("qualquer@test.com"), "10.0.0.1");

        var ordem = inOrder(passwordEncoder, usuarioRepository);
        ordem.verify(passwordEncoder).encode("SenhaBoa123");
        ordem.verify(usuarioRepository).findByEmail("qualquer@test.com");
    }

    @Test
    void solicitar_NormalizaEmailComEspacoEMaiuscula() {
        service.solicitar(pedido("  Joao@Test.COM  "), "10.0.0.1");

        verify(usuarioRepository).findByEmail("joao@test.com");
    }

    // ─── Honeypot e anti-bot ──────────────────────────────────────────────────

    @Test
    void solicitar_HoneypotPreenchido_DescartaEmSilencioSemLancar() {
        var comIsca = new RegistroDTO.Solicitacao("Bot Ltda", "Bot", "bot@test.com",
                "SenhaBoa123", true, "token", "http://spam.com");

        assertDoesNotThrow(() -> service.solicitar(comIsca, "10.0.0.1"));

        verify(pendenteRepository, never()).save(any());
        verify(emailService, never()).enviar(any(), any(), any());
        // Nem chega a gastar BCrypt: o bot não custa CPU nem consulta.
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void solicitar_TurnstileRecusado_NaoCriaPendente() {
        when(turnstileValidator.valido(any(), any())).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.solicitar(pedido("bot@test.com"), "10.0.0.1"));

        verify(pendenteRepository, never()).save(any());
        verify(emailService, never()).enviar(any(), any(), any());
    }

    @Test
    void solicitar_SenhaFraca_RejeitaSemCriarPendente() {
        var fraca = new RegistroDTO.Solicitacao("Loja", "Fulano", "f@test.com",
                "123", true, "token", null);

        assertThrows(IllegalArgumentException.class, () -> service.solicitar(fraca, "10.0.0.1"));

        verify(pendenteRepository, never()).save(any());
    }

    // ─── O banco nunca vê o token cru ─────────────────────────────────────────

    @Test
    void solicitar_GravaApenasHash_NuncaOTokenDoLink() {
        service.solicitar(pedido("joao@test.com"), "10.0.0.1");

        var salvo = ArgumentCaptor.forClass(RegistroPendente.class);
        var corpo = ArgumentCaptor.forClass(String.class);
        verify(pendenteRepository).save(salvo.capture());
        verify(emailService).enviar(anyString(), anyString(), corpo.capture());

        var hash = salvo.getValue().getTokenHash();
        assertEquals(64, hash.length(), "SHA-256 em hex tem 64 caracteres");
        assertFalse(corpo.getValue().contains(hash), "o hash do banco não pode vazar no email");
        assertTrue(corpo.getValue().contains("https://app.teste/registro/confirmar?token="));
        assertNotEquals("SenhaBoa123", salvo.getValue().getSenhaHash(), "senha em claro nunca é persistida");
    }

    // ─── Rate limit nas duas dimensões ────────────────────────────────────────

    @Test
    void solicitar_EstouraTetoPorIp_Lanca429() {
        // Mesmo IP, emails diferentes: só o teto por IP pode disparar.
        for (int i = 0; i < 5; i++) {
            service.solicitar(pedido("email" + i + "@test.com"), "10.0.0.9");
        }

        assertThrows(TooManyRequestsException.class,
                () -> service.solicitar(pedido("email99@test.com"), "10.0.0.9"));
    }

    @Test
    void solicitar_EstouraTetoPorEmail_Lanca429() {
        // IPs diferentes, mesmo email: só o teto por email pode disparar.
        for (int i = 0; i < 3; i++) {
            service.solicitar(pedido("alvo@test.com"), "10.0.1." + i);
        }

        assertThrows(TooManyRequestsException.class,
                () -> service.solicitar(pedido("alvo@test.com"), "10.0.1.99"));
    }

    // ─── Token: inexistente, expirado e já usado dão a MESMA resposta ─────────

    @Test
    void confirmar_TokenInexistente_ErroGenerico() {
        when(pendenteRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        var ex = assertThrows(IllegalArgumentException.class, () -> service.confirmar("qualquer"));
        assertEquals("Link inválido ou expirado.", ex.getMessage());
        verify(empresaService, never()).criar(any());
    }

    @Test
    void confirmar_TokenExpirado_MesmoErroGenerico() {
        when(pendenteRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(pendente(LocalDateTime.now().minusMinutes(1))));

        var ex = assertThrows(IllegalArgumentException.class, () -> service.confirmar("qualquer"));
        assertEquals("Link inválido ou expirado.", ex.getMessage());
        verify(empresaService, never()).criar(any());
    }

    /**
     * Token já usado é indistinguível de inexistente porque a linha é APAGADA na
     * confirmação — o uso único sai de graça, sem coluna de controle.
     */
    @Test
    void confirmar_TokenValido_ApagaOPendente() {
        prepararProvisionamento();

        service.confirmar("token-cru");

        verify(pendenteRepository).delete(any(RegistroPendente.class));
    }

    // ─── Provisionamento ──────────────────────────────────────────────────────

    @Test
    void confirmar_ProvisionaTenantCompletoELogaAutomaticamente() {
        prepararProvisionamento();

        var resultado = service.confirmar("token-cru");

        verify(empresaService).criar(argThat(r -> r.nome().equals("Mercado do João")));
        verify(usuarioService).criarAdminDoRegistro(EMPRESA_ID, "João Batista", "joao@test.com",
                "$2a$12$hash-falso", EtapaOnboarding.LEMBRETES);
        assertEquals("jwt-falso", resultado.accessToken());
        assertEquals("refresh-falso", resultado.refreshToken());
        assertEquals(PerfilUsuario.ADMIN, resultado.usuario().perfil());
    }

    /** A loja nasce com o mesmo nome da empresa — o formulário perguntou uma vez só. */
    @Test
    void confirmar_CriaLojaComOMesmoNomeDaEmpresa() {
        prepararProvisionamento();

        service.confirmar("token-cru");

        var loja = ArgumentCaptor.forClass(CriarLojaRequest.class);
        verify(lojaService).criarNaEmpresa(eq(EMPRESA_ID), loja.capture());
        assertEquals("Mercado do João", loja.getValue().nome());
    }

    /**
     * Corrida de dois pendentes do mesmo email: o segundo é barrado ANTES de
     * criar a empresa. Sem essa checagem, o unique de {@code usuarios.email} só
     * estouraria no fim, e uma falha de rollback deixaria empresa, loja e trial
     * órfãos.
     */
    @Test
    void confirmar_EmailJaVirouConta_NaoProvisionaNada() {
        prepararProvisionamento();
        when(usuarioRepository.findByEmail("joao@test.com")).thenReturn(Optional.of(new Usuario()));

        assertThrows(IllegalArgumentException.class, () -> service.confirmar("token-cru"));

        verify(empresaService, never()).criar(any());
        verify(lojaService, never()).criarNaEmpresa(any(), any());
        verify(usuarioService, never()).criarAdminDoRegistro(any(), any(), any(), any(), any());
    }

    // ─── A restrição que derrubou a primeira versão ───────────────────────────

    /**
     * O {@code AuditoriaService} roda em {@code REQUIRES_NEW} e a FK
     * {@code auditoria.empresa_id} não enxerga uma empresa ainda não commitada.
     * Qualquer auditoria durante a transação estoura e derruba o cadastro
     * inteiro — foi exatamente o que aconteceu na primeira versão.
     */
    @Test
    void confirmar_NaoAuditaDuranteATransacao_MasAuditaDepoisDoCommit() {
        prepararProvisionamento();

        service.confirmar("token-cru");
        verify(auditoriaService, never()).registrar(any(), any(), any(), any());

        disparaAfterCommit();
        verify(auditoriaService).registrar(eq("REGISTRO"), eq("EMPRESA"), eq(EMPRESA_ID), contains("joao@test.com"));
    }

    /**
     * O {@code TenantContext} tem de ficar VAZIO durante o provisionamento: é o
     * que faz {@code AuditoriaService.registrar} se abster sozinho dentro dos
     * services que ele orquestra. Preenchê-lo reintroduz a violação de FK acima.
     */
    @Test
    void confirmar_MantemTenantContextVazioDuranteOProvisionamento() {
        prepararProvisionamento();
        var tenantDurante = new UUID[1];
        when(lojaService.criarNaEmpresa(any(), any())).thenAnswer(i -> {
            tenantDurante[0] = TenantContext.getEmpresaId();
            return null;
        });

        service.confirmar("token-cru");

        assertNull(tenantDurante[0],
                "TenantContext preenchido faria a auditoria tentar gravar antes do commit e estourar a FK");
    }

    private void disparaAfterCommit() {
        List.copyOf(TransactionSynchronizationManager.getSynchronizations())
                .forEach(TransactionSynchronization::afterCommit);
    }
}
