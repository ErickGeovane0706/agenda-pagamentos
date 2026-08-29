package com.agenda.security;

import com.agenda.domain.empresa.EmpresaService;
import com.agenda.shared.TenantContext;
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
import java.util.UUID;

/**
 * Gate do módulo de Estoque/PDV: bloqueia {@code /api/produtos} e
 * {@code /api/vendas} para empresa que não teve o módulo liberado pelo MASTER.
 * Um filtro só, espelhando o {@link AssinaturaGateFilter} — o padrão já provado.
 * <p>
 * Três decisões que parecem detalhe e não são:
 * <ul>
 *   <li><b>403, nunca 402.</b> O 402 significa "pague a assinatura" e o frontend
 *       redireciona para {@code /assinar}. Módulo não contratado não é
 *       inadimplência: mandar o cliente para uma tela de pagamento que não
 *       libera nada seria pior que o bloqueio.</li>
 *   <li><b>Bloqueia LEITURA também</b>, ao contrário do gate de assinatura. Lá o
 *       inadimplente vê os próprios dados e só não escreve; aqui não há o que
 *       ver, porque o módulo nunca existiu para essa empresa.</li>
 *   <li><b>Lookup no banco, nunca claim no JWT.</b> Mesma regra da assinatura: se
 *       o flag vivesse no token, o MASTER liberaria e o cliente continuaria
 *       bloqueado até relogar — sem ninguém entender por quê.</li>
 * </ul>
 * Requisição sem tenant passa direto: sem JWT quem responde 401 é o Spring
 * Security, e o MASTER (que não tem tenant próprio) não opera estas rotas.
 */
@Component
@RequiredArgsConstructor
public class PdvGateFilter extends OncePerRequestFilter {

    private static final String MENSAGEM_BLOQUEIO =
        "O módulo de Estoque e PDV não está liberado para a sua empresa.";

    private final EmpresaService empresaService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (deveBloquear(request)) {
            responder403(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean deveBloquear(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/produtos") && !uri.startsWith("/api/vendas")) {
            return false;
        }
        UUID empresaId = TenantContext.getEmpresaId();
        if (empresaId == null) {
            return false;
        }
        return !empresaService.pdvHabilitado(empresaId);
    }

    private void responder403(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        var body = new ErrorResponse(HttpStatus.FORBIDDEN.value(),
            MENSAGEM_BLOQUEIO, null, LocalDateTime.now());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
