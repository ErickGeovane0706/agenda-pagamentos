package com.agenda.shared;

import java.util.UUID;

public class UserContext {
    private static final ThreadLocal<UUID> currentUserId = new ThreadLocal<>();
    private static final ThreadLocal<String> currentUserName = new ThreadLocal<>();
    private static final ThreadLocal<String> currentPerfil = new ThreadLocal<>();

    public static void set(UUID usuarioId, String nome, String perfil) {
        currentUserId.set(usuarioId);
        currentUserName.set(nome);
        currentPerfil.set(perfil);
    }

    public static UUID getUsuarioId() {
        return currentUserId.get();
    }

    public static String getNome() {
        return currentUserName.get();
    }

    public static String getPerfil() {
        return currentPerfil.get();
    }

    public static boolean isMaster() {
        return "MASTER".equals(currentPerfil.get());
    }

    public static void clear() {
        currentUserId.remove();
        currentUserName.remove();
        currentPerfil.remove();
    }
}
