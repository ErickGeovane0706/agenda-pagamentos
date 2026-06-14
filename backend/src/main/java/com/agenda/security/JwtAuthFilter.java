package com.agenda.security;

import com.agenda.shared.TenantContext;
import com.agenda.shared.UserContext;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }

            String token = authHeader.substring(7);

            try {
                String email = jwtService.extractEmail(token);

                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    var userDetails = userDetailsService.loadUserByUsername(email);
                    if (jwtService.isValid(token, userDetails)) {
                        var empresaId = jwtService.extractEmpresaId(token);
                        TenantContext.setEmpresaId(empresaId);

                        var usuarioId = jwtService.extractUsuarioId(token);
                        var nome = jwtService.extractNome(token);
                        var perfil = jwtService.extractPerfil(token);
                        UserContext.set(usuarioId, nome, perfil);

                        var auth = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            } catch (ExpiredJwtException e) {
                log.debug("Token expirado ignorado: {}", e.getMessage());
            }

            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            UserContext.clear();
        }
    }
}
