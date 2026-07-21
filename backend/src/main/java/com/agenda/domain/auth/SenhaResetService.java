package com.agenda.domain.auth;

import com.agenda.domain.usuario.PasswordValidator;
import com.agenda.domain.usuario.UsuarioRepository;
import com.agenda.email.EmailService;
import com.agenda.security.RateLimiterService;
import com.agenda.security.RefreshTokenService;
import com.agenda.shared.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Redefinição de senha self-service ("esqueci minha senha").
 * <p>
 * Duas garantias que moldam o desenho:
 * <ol>
 *   <li><b>Anti-enumeração:</b> {@code solicitar} nunca revela se o email
 *       existe. Retorna void, e o controller responde igual nos dois casos —
 *       do contrário o endpoint vira um oráculo público de quem é cliente.</li>
 *   <li><b>Token só existe em claro no email:</b> o banco guarda o SHA-256.
 *       Vazamento do banco não permite redefinir senha de ninguém.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class SenhaResetService {

    private static final Logger log = LoggerFactory.getLogger(SenhaResetService.class);

    /** Teto por IP: barra varredura de emails a partir de uma origem. */
    private static final int MAX_POR_IP = 5;
    private static final Duration JANELA_IP = Duration.ofHours(1);

    /** Teto por email: barra usar a caixa de entrada de alguém como spam. */
    private static final int MAX_POR_EMAIL = 3;
    private static final Duration JANELA_EMAIL = Duration.ofDays(1);

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SenhaResetTokenRepository tokenRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordValidator passwordValidator;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final RateLimiterService rateLimiter;
    private final EmailService emailService;

    @Value("${senha-reset.ttl-horas:2}")
    private int ttlHoras;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Solicita a redefinição. Sempre "bem-sucedido" do ponto de vista de quem
     * chama: email inexistente segue o mesmo caminho, só não gera token nem
     * envia nada.
     *
     * @throws TooManyRequestsException quando o teto por IP ou por email estoura
     */
    @Transactional
    public void solicitar(String email, String ip) {
        var normalizado = email == null ? "" : email.trim().toLowerCase();

        if (!rateLimiter.tentarConsumir("reset-ip:" + ip, MAX_POR_IP, JANELA_IP)
                || !rateLimiter.tentarConsumir("reset-email:" + normalizado, MAX_POR_EMAIL, JANELA_EMAIL)) {
            throw new TooManyRequestsException("Muitas solicitações. Tente novamente mais tarde.");
        }

        var usuario = usuarioRepository.findByEmail(normalizado).orElse(null);
        if (usuario == null) {
            // Silencioso de propósito: quem pediu recebe a mesma resposta.
            log.info("[RESET] Solicitação para email não cadastrado — nenhum token gerado.");
            return;
        }

        var tokenCru = gerarToken();
        tokenRepository.save(SenhaResetToken.builder()
            .usuarioId(usuario.getId())
            .tokenHash(hash(tokenCru))
            .expiraEm(LocalDateTime.now().plusHours(ttlHoras))
            .build());

        var link = frontendUrl + "/redefinir-senha?token=" + tokenCru;
        emailService.enviar(usuario.getEmail(), "Redefinição de senha", corpoEmail(usuario.getNome(), link));
        log.info("[RESET] Token gerado para usuário {} (expira em {}h).", usuario.getId(), ttlHoras);
    }

    /**
     * Redefine a senha a partir do token recebido por email.
     * Revoga todos os refresh tokens: trocar a senha derruba as sessões
     * abertas, inclusive a de quem tenha roubado a conta.
     *
     * @throws IllegalArgumentException token inválido, expirado ou já usado —
     *         sempre com a MESMA mensagem, para não distinguir os casos
     */
    @Transactional
    public void redefinir(String tokenCru, String novaSenha) {
        var token = tokenRepository.findByTokenHash(hash(tokenCru))
            .orElseThrow(() -> new IllegalArgumentException("Link inválido ou expirado."));

        if (token.getUsadoEm() != null || token.getExpiraEm().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Link inválido ou expirado.");
        }

        var usuario = usuarioRepository.findById(token.getUsuarioId())
            .orElseThrow(() -> new IllegalArgumentException("Link inválido ou expirado."));

        passwordValidator.validar(novaSenha);
        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);

        token.setUsadoEm(LocalDateTime.now());
        tokenRepository.save(token);

        refreshTokenService.revokeAll(usuario.getId());
        log.info("[RESET] Senha redefinida para usuário {} — sessões revogadas.", usuario.getId());
    }

    /** Remove tokens vencidos ou já usados. Chamado pelo job diário. */
    @Transactional
    public int limparExpirados() {
        return tokenRepository.deleteExpirados(LocalDateTime.now());
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

    private String corpoEmail(String nome, String link) {
        return """
            <p>Olá, %s.</p>
            <p>Recebemos um pedido para redefinir a senha da sua conta na Agenda de Pagamentos.
            O link abaixo vale por %d hora(s) e só pode ser usado uma vez:</p>
            <p><a href="%s">Redefinir minha senha</a></p>
            <p>Se você não pediu isso, ignore este email — sua senha continua a mesma.</p>
            """.formatted(nome, ttlHoras, link);
    }
}
