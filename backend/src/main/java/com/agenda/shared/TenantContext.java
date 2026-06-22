package com.agenda.shared;

import java.util.UUID;

/**
 * Contexto de tenant para isolamento multi-tenant por requisição.
 *
 * Armazena o ID da empresa (tenant) associado à requisição atual
 * em um ThreadLocal. O valor é setado pelo JwtAuthFilter ao extrair
 * o claim "empresaId" do token JWT e limpo no finally do filtro
 * para evitar vazamento entre requisições.
 *
 * Regra de negócio: todas as consultas ao banco devem filtrar por
 * empresa_id usando o valor retornado por getEmpresaId(), garantindo
 * que usuários de uma empresa nunca acessem dados de outra.
 */
public class TenantContext {

    private static final ThreadLocal<UUID> empresaIdHolder = new ThreadLocal<>();

    public static void setEmpresaId(UUID empresaId) {
        empresaIdHolder.set(empresaId);
    }

    public static UUID getEmpresaId() {
        return empresaIdHolder.get();
    }

    public static void clear() {
        empresaIdHolder.remove();
    }
}
