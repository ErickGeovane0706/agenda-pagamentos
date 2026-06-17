package com.agenda.domain.auth;

import com.agenda.domain.usuario.UsuarioDTO;

public record AuthLoginResult(String accessToken, String refreshToken, UsuarioDTO usuario) {}
