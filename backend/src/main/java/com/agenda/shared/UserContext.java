package com.agenda.shared;

import java.util.UUID;

/**
 * Contexto do usuário autenticado na requisição atual.
 *
 * Armazena ID, nome e perfil do usuário em ThreadLocals para acesso
 * rápido em camadas de serviço sem precisar propagar parâmetros
 * manualmente. Populado pelo JwtAuthFilter e limpo no finally.
 *
 * Regra de negócio: o campo isMaster() permite que operações
 * administrativas (ex.: gerenciar empresas) só sejam executadas
 * por usuários com perfil MASTER.
 */
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

    /**
     * Verifica se o usuário atual tem perfil MASTER (super-admin).
     * Usado em guardas de autorização para funcionalidades globais.
     */
    public static boolean isMaster() {
        return "MASTER".equals(currentPerfil.get());
    }

    public static void clear() {
        currentUserId.remove();
        currentUserName.remove();
        currentPerfil.remove();
    }
}
