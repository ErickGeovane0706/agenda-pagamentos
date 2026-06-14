package com.agenda.shared;

import java.util.UUID;

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
