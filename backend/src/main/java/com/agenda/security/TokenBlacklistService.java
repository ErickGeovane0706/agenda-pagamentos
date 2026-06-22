package com.agenda.security;

import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blacklist de tokens JWT em memória (ConcurrentHashMap).
 * Usada no logout para invalidar o access token antes da
 * expiração natural. A limpeza de entradas expiradas ocorre
 * sob demanda durante validações.
 */
@Service
public class TokenBlacklistService {
    /**
     * Mapa concorrente de JTI → data de expiração do token.
     * Usado para invalidar tokens de sessões que fizeram logout.
     * A limpeza é feita sob demanda para evitar acúmulo de entradas expiradas.
     */
    private final ConcurrentHashMap<String, Date> blacklist = new ConcurrentHashMap<>();

    /**
     * Adiciona o JTI à blacklist. O token correspondente não será mais aceito.
     * Chamado durante logout.
     */
    public void add(String jti, Date expiresAt) {
        blacklist.put(jti, expiresAt);
        cleanup();
    }

    /**
     * Verifica se um JTI está na blacklist (token foi invalidado via logout).
     */
    public boolean isBlacklisted(String jti) {
        cleanup();
        return blacklist.containsKey(jti);
    }

    /**
     * Remove entradas cuja data de expiração já passou,
     * evitando vazamento de memória sem necessidade de scheduler externo.
     */
    private void cleanup() {
        var now = new Date();
        blacklist.values().removeIf(exp -> exp.before(now));
    }
}
