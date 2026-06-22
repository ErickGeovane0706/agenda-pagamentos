package com.agenda.domain.usuario;

/**
 * Perfis de acesso no sistema.
 * Hierarquia: MASTER (super-admin multi-tenant) > ADMIN (gestor da empresa)
 * > OPERADOR (opera pagamentos) > VIEWER (apenas consulta).
 */
public enum PerfilUsuario {
    MASTER, ADMIN, OPERADOR, VIEWER
}
