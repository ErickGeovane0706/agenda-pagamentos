package com.agenda.domain.auth;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.domain.empresa.CriarEmpresaRequest;
import com.agenda.domain.empresa.EmpresaService;
import com.agenda.domain.loja.CriarLojaRequest;
import com.agenda.domain.loja.LojaService;
import com.agenda.domain.usuario.EtapaOnboarding;
import com.agenda.domain.usuario.PasswordValidator;
import com.agenda.domain.usuario.UsuarioDTO;
import com.agenda.domain.usuario.UsuarioRepository;
import com.agenda.domain.usuario.UsuarioService;
import com.agenda.email.EmailService;
import com.agenda.security.JwtService;
import com.agenda.security.RateLimiterService;
import com.agenda.security.RefreshTokenService;
import com.agenda.security.TurnstileValidator;
import com.agenda.shared.TenantContext;
import com.agenda.shared.UserContext;
import com.agenda.shared.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Cadastro público (signup self-service), verify-first.
 * <p>
 * Este é o primeiro endpoint de ESCRITA sem autenticação do sistema, e ele não
 * cria só um usuário: provisiona um tenant inteiro — empresa, loja, usuário
 * ADMIN e assinatura TRIAL — sem humano no circuito. Daí a quantidade de
 * guardas.
 * <p>
 * Segue o molde do {@link SenhaResetService}, que estreou este maquinário no
 * fluxo simples de propósito: token de 32 bytes, banco guarda só o SHA-256, uso
 * único, TTL, resposta neutra e rate limit em duas dimensões.
 * <p>
 * <b>Verify-first:</b> nada é criado no pedido. O tenant só nasce no clique do
 * link ({@link #confirmar}). Um bot que confirme nada deixa apenas uma linha em
 * {@code registro_pendente}, apagada por job — em vez de exigir um fluxo de
 * exclusão de tenant, que seria o caminho mais perigoso do sistema.
 */
@Service
@RequiredArgsConstructor
public class RegistroService {

    private static final Logger log = LoggerFactory.getLogger(RegistroService.class);

    /** Mesmos tetos do reset de senha: barra varredura a partir de um IP. */
    private static final int MAX_POR_IP = 5;
    private static final Duration JANELA_IP = Duration.ofHours(1);

    /** Barra usar a caixa de entrada de alguém como alvo de spam. */
    private static final int MAX_POR_EMAIL = 3;
    private static final Duration JANELA_EMAIL = Duration.ofDays(1);

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Mensagem única para token inexistente, expirado ou já usado. */
    private static final String LINK_INVALIDO = "Link inválido ou expirado.";

    private final RegistroPendenteRepository pendenteRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmpresaService empresaService;
    private final LojaService lojaService;
    private final UsuarioService usuarioService;
    private final PasswordValidator passwordValidator;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiterService rateLimiter;
    private final TurnstileValidator turnstileValidator;
    private final EmailService emailService;
    private final AuditoriaService auditoriaService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Value("${registro.ttl-horas:48}")
    private int ttlHoras;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Recebe o pedido de cadastro. Do ponto de vista de quem chama, sempre
     * "bem-sucedido": email livre e email já cadastrado seguem o mesmo caminho e
     * recebem a mesma resposta — o que muda é qual email a pessoa recebe.
     * <p>
     * Distinguir os dois transformaria o cadastro num oráculo público de quem é
     * cliente do sistema.
     *
     * @throws TooManyRequestsException  teto por IP ou por email estourado
     * @throws IllegalArgumentException  anti-bot recusado ou senha fraca — estes
     *                                   SÃO revelados, porque são falhas do
     *                                   próprio requisitante e ele precisa poder
     *                                   corrigir. Nenhum deles diz nada sobre a
     *                                   existência do email.
     */
    @Transactional
    public void solicitar(RegistroDTO.Solicitacao req, String ip) {
        // Honeypot: campo escondido por CSS que humano nunca vê. Descarte
        // silencioso — o bot recebe 204 e acredita ter conseguido, o que é
        // melhor do que ensiná-lo a contornar.
        if (req.website() != null && !req.website().isBlank()) {
            log.info("[REGISTRO] Honeypot preenchido — pedido descartado em silêncio.");
            return;
        }

        var email = req.email() == null ? "" : req.email().trim().toLowerCase();

        if (!rateLimiter.tentarConsumir("registro-ip:" + ip, MAX_POR_IP, JANELA_IP)
                || !rateLimiter.tentarConsumir("registro-email:" + email, MAX_POR_EMAIL, JANELA_EMAIL)) {
            throw new TooManyRequestsException("Muitas tentativas. Tente novamente mais tarde.");
        }

        if (!turnstileValidator.valido(req.turnstileToken(), ip)) {
            throw new IllegalArgumentException("Verificação de segurança falhou. Recarregue a página e tente de novo.");
        }

        passwordValidator.validar(req.senha());

        // O BCrypt vem ANTES da consulta de propósito. Ele custa ~250ms, e se
        // rodasse só no caminho do email livre, a diferença de latência entre os
        // dois caminhos seria um oráculo de enumeração tão bom quanto uma
        // mensagem de erro diferente.
        var senhaHash = passwordEncoder.encode(req.senha());

        if (usuarioRepository.findByEmail(email).isPresent()) {
            emailService.enviar(email, "Sobre sua conta", corpoJaTemConta());
            log.info("[REGISTRO] Pedido para email já cadastrado — aviso enviado, nada criado.");
            return;
        }

        var tokenCru = gerarToken();
        pendenteRepository.save(RegistroPendente.builder()
            .nomeNegocio(req.nomeNegocio().trim())
            .nomeUsuario(req.nome().trim())
            .email(email)
            .senhaHash(senhaHash)
            .tokenHash(hash(tokenCru))
            .expiraEm(LocalDateTime.now().plusHours(ttlHoras))
            .build());

        var link = frontendUrl + "/registro/confirmar?token=" + tokenCru;
        emailService.enviar(email, "Confirme seu cadastro", corpoConfirmacao(req.nome().trim(), link));
        log.info("[REGISTRO] Cadastro pendente criado (expira em {}h).", ttlHoras);
    }

    /**
     * Confirma o email e provisiona o tenant inteiro, numa transação só.
     * <p>
     * Orquestra os services que já existem — nunca reimplementa criação. É o que
     * garante que a regra de negócio de cada entidade (o trial que nasce junto
     * com a empresa, o teto de lojas) valha igual aqui e no fluxo do MASTER.
     * <p>
     * <b>O {@code TenantContext} fica vazio de propósito durante a transação.</b>
     * Parece descuido e não é: o {@code AuditoriaService} roda em
     * {@code REQUIRES_NEW} — para a trilha sobreviver à falha do negócio — e uma
     * transação separada <b>não enxerga a empresa que ainda não foi commitada</b>,
     * então a FK {@code auditoria.empresa_id} estouraria e derrubaria o cadastro
     * inteiro. Com o ThreadLocal nulo, {@code registrar} se abstém sozinho
     * (retorna cedo) e a trilha é escrita depois do commit, por
     * {@link #auditarAposCommit}. É também por isso que este método usa
     * {@code lojaService.criarNaEmpresa} em vez de {@code criar}.
     *
     * @return credenciais para login automático — o cliente cai no sistema já
     *         logado, sem digitar a senha que acabou de criar
     * @throws IllegalArgumentException token inválido, expirado ou já usado,
     *         sempre com a MESMA mensagem
     */
    @Transactional
    public AuthLoginResult confirmar(String tokenCru) {
        var pendente = pendenteRepository.findByTokenHash(hash(tokenCru))
            .orElseThrow(() -> new IllegalArgumentException(LINK_INVALIDO));

        if (pendente.getExpiraEm().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(LINK_INVALIDO);
        }

        // Barra a corrida ANTES de criar a empresa: sem isso, o unique de
        // usuarios.email só estouraria no fim, deixando empresa, loja e trial
        // órfãos caso a transação não desfizesse tudo.
        if (usuarioRepository.findByEmail(pendente.getEmail()).isPresent()) {
            log.info("[REGISTRO] Confirmação de email que já virou conta — nada provisionado.");
            throw new IllegalArgumentException("Esse email já possui uma conta. Faça login.");
        }

        // Empresa já nasce com assinatura TRIAL e vigência preenchida.
        var empresa = empresaService.criar(new CriarEmpresaRequest(pendente.getNomeNegocio()));

        // Mesmo nome da empresa: para dono de loja pequena os dois são a mesma
        // coisa, e o formulário perguntou uma vez só.
        lojaService.criarNaEmpresa(empresa.id(),
            new CriarLojaRequest(pendente.getNomeNegocio(), null, null, null));

        var usuario = usuarioService.criarAdminDoRegistro(
            empresa.id(), pendente.getNomeUsuario(), pendente.getEmail(),
            pendente.getSenhaHash(), EtapaOnboarding.LEMBRETES);

        // Apagar em vez de marcar como usado: dá uso único de graça e não deixa
        // senha e email guardados em duplicidade agora que a pessoa virou
        // usuário de verdade.
        pendenteRepository.delete(pendente);

        auditarAposCommit(empresa.id(), usuario);

        var entidade = usuarioRepository.getReferenceById(usuario.id());
        return new AuthLoginResult(
            jwtService.generateToken(entidade),
            refreshTokenService.create(entidade).getToken(),
            usuario);
    }

    /**
     * Agenda o registro da trilha para depois do commit — o único momento em que
     * a empresa existe para a transação separada da auditoria.
     * <p>
     * Um evento só ("REGISTRO") em vez dos três CRIAR que os services emitiriam:
     * no cadastro self-service não há um usuário que executou a ação, e o que
     * interessa numa investigação é que este tenant nasceu sozinho, quando, e
     * com que email.
     */
    private void auditarAposCommit(UUID empresaId, UsuarioDTO usuario) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    TenantContext.setEmpresaId(empresaId);
                    UserContext.set(usuario.id(), usuario.nome(), usuario.perfil().name());
                    auditoriaService.registrar("REGISTRO", "EMPRESA", empresaId,
                        "Cadastro público concluído: " + usuario.email());
                } finally {
                    TenantContext.clear();
                    UserContext.clear();
                }
            }
        });
    }

    /** Remove cadastros pendentes vencidos. Chamado pelo job diário. */
    @Transactional
    public int limparExpirados() {
        return pendenteRepository.deleteExpirados(LocalDateTime.now());
    }

    private String gerarToken() {
        var bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 em hex: 64 caracteres, o tamanho exato da coluna token_hash. */
    private String hash(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }

    private String corpoConfirmacao(String nome, String link) {
        return """
            <p>Olá, %s.</p>
            <p>Falta um passo para sua área ficar pronta. O link abaixo vale por %d horas
            e só pode ser usado uma vez:</p>
            <p><a href="%s">Confirmar meu cadastro</a></p>
            <p>Se você não pediu este cadastro, ignore este email — nada foi criado.</p>
            """.formatted(nome, ttlHoras, link);
    }

    /**
     * Resposta ao pedido de cadastro com email já cadastrado. Não confirma nada
     * a quem não é dono da caixa: quem recebe é o titular, e para ele a
     * informação é útil.
     */
    private String corpoJaTemConta() {
        return """
            <p>Olá.</p>
            <p>Recebemos um pedido de cadastro com este email, mas você já tem uma conta.</p>
            <p><a href="%s/login">Entrar na minha conta</a> —
            se esqueceu a senha, use a opção "Esqueci minha senha" na tela de login.</p>
            <p>Se não foi você, pode ignorar este email.</p>
            """.formatted(frontendUrl);
    }
}
