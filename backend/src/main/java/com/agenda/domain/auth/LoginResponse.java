package com.agenda.domain.auth;

import com.agenda.domain.usuario.UsuarioDTO;

public record LoginResponse(String token, UsuarioDTO usuario) {}
