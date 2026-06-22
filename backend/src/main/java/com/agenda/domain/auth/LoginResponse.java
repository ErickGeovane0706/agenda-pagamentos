package com.agenda.domain.auth;

import com.agenda.domain.usuario.UsuarioDTO;

/**
 * Resposta pública do login: contém apenas os dados do usuário.
 * Tokens são enviados via cookies httpOnly (não expostos ao JS).
 */
public record LoginResponse(UsuarioDTO usuario) {}
