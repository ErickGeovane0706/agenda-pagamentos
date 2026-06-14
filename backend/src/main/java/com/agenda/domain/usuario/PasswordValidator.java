package com.agenda.domain.usuario;

import org.springframework.stereotype.Component;

@Component
public class PasswordValidator {

    public void validar(String senha) {
        if (senha == null || senha.length() < 8) {
            throw new IllegalArgumentException("Senha deve ter no mínimo 8 caracteres");
        }
        if (!senha.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("Senha deve conter pelo menos uma letra maiúscula");
        }
        if (!senha.matches(".*[a-z].*")) {
            throw new IllegalArgumentException("Senha deve conter pelo menos uma letra minúscula");
        }
        if (!senha.matches(".*\\d.*")) {
            throw new IllegalArgumentException("Senha deve conter pelo menos um número");
        }
    }
}
