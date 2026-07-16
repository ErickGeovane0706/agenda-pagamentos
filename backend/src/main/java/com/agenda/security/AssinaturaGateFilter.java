package com.agenda.security;

import com.agenda.domain.assinatura.AssinaturaService;
import com.agenda.shared.TenantContext;
import com.agenda.shared.UserContext;
import com.agenda.shared.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Gate de assinatura para requisições HTTP: empresa inadimplente ou
 * cancelada fica em modo somente-leitura. Um único filtro, em vez de
 * checagem espalhada pelos services.
 *
 * Bloqueia (402) apenas métodos de ESCRITA em /api/** quando a assinatura
 * da empresa não está acessável. GET continua liberado: o cliente
 * inadimplente VÊ os dados dele, só não opera.
 *
 * Passam sem checagem:
 * - /api/auth/** (login/refresh/logout sempre funcionam);
 * - /api/lgpd/** (direito legal independe de pagamento);
 * - /api/assinaturas/** (é por onde se paga: bloquear o inadimplente aqui o
 *   impediria de assinar e o prenderia no bloqueio para sempre);
 * - usuário MASTER (por papel, não por path — senão não reativa ninguém);
 * - requisição sem tenant (sem JWT: o Spring Security responde 401);
 * - tudo fora de /api/** (ex.: /webhook/**, que não tem JWT e é gateado
 *   explicitamente no agente).
 *
 * Roda depois do JwtAuthFilter (que popula o TenantContext): o filtro do
 * JWT participa da security filter chain (ordem -100) e este é registrado
 * como filtro comum do servlet, que executa depois dela.
 */
@Component
@RequiredArgsConstructor
public class AssinaturaGateFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS_ESCRITA = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String MENSAGEM_BLOQUEIO =
        "O acesso da sua empresa está suspenso. Regularize a assinatura para voltar a operar.";

    private final AssinaturaService assinaturaService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (deveBloquear(request)) {
            responder402(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean deveBloquear(HttpServletRequest request) {
        if (!METODOS_ESCRITA.contains(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/") || uri.startsWith("/api/auth/")
                || uri.startsWith("/api/lgpd/") || uri.startsWith("/api/assinaturas")) {
            return false;
        }
        if (UserContext.isMaster()) {
            return false;
        }
        UUID empresaId = TenantContext.getEmpresaId();
        if (empresaId == null) {
            return false;
        }
        return !assinaturaService.podeAcessar(empresaId);
    }

    private void responder402(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.PAYMENT_REQUIRED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        var body = new ErrorResponse(HttpStatus.PAYMENT_REQUIRED.value(),
            MENSAGEM_BLOQUEIO, null, LocalDateTime.now());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
