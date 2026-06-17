package com.agenda.security;

import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBlacklistService {
    private final ConcurrentHashMap<String, Date> blacklist = new ConcurrentHashMap<>();

    public void add(String jti, Date expiresAt) {
        blacklist.put(jti, expiresAt);
        cleanup();
    }

    public boolean isBlacklisted(String jti) {
        cleanup();
        return blacklist.containsKey(jti);
    }

    private void cleanup() {
        var now = new Date();
        blacklist.values().removeIf(exp -> exp.before(now));
    }
}
