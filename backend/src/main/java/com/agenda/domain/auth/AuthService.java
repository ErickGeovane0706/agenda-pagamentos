package com.agenda.domain.auth;

import com.agenda.auditoria.AuditoriaService;
import com.agenda.domain.usuario.UsuarioDTO;
import com.agenda.domain.usuario.UsuarioRepository;
import com.agenda.security.JwtService;
import com.agenda.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AuditoriaService auditoriaService;

    public LoginResponse login(AuthDTO dto) {
        var email = dto.email();
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, dto.senha()));
        } catch (Exception e) {
            auditoriaService.registrar("LOGIN_FALHA", "USUARIO", null, "Email: " + email);
            throw e;
        }

        var usuario = usuarioRepository.findByEmail(email)
            .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));

        if (!usuario.getAtivo()) {
            auditoriaService.registrar("LOGIN_FALHA", "USUARIO", usuario.getId(), "Email: " + email + " (inativo)");
            throw new NotFoundException("Usuário inativo");
        }

        String token = jwtService.generateToken(usuario);
        auditoriaService.registrar("LOGIN", "USUARIO", usuario.getId(), "Email: " + email);
        return new LoginResponse(token, UsuarioDTO.from(usuario));
    }
}
