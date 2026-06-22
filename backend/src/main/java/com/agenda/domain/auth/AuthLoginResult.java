package com.agenda.domain.auth;

import com.agenda.domain.usuario.UsuarioDTO;

/**
 * Resultado interno do login/refresh: access token JWT,
 * refresh token UUID e dados do usuário autenticado.
 */
public record AuthLoginResult(String accessToken, String refreshToken, UsuarioDTO usuario) {}
