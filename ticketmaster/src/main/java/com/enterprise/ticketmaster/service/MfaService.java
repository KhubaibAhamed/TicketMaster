package com.enterprise.ticketmaster.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Holds short-lived "password verified, awaiting TOTP code" login sessions. In-memory only — losing these on restart just forces a re-login, which is safe. */
@Service
public class MfaService {

    private static final long PENDING_LOGIN_TTL_SECONDS = 300; // 5 minutes to enter the code

    private final Map<String, PendingLogin> pendingLogins = new ConcurrentHashMap<>();

    public String createPendingLogin(String username) {
        String token = UUID.randomUUID().toString();
        pendingLogins.put(token, new PendingLogin(username, Instant.now().plusSeconds(PENDING_LOGIN_TTL_SECONDS)));
        return token;
    }

    /** Returns the username if valid+unexpired, consuming the token (one-time use). Null otherwise. */
    public String consumePendingLogin(String mfaToken) {
        PendingLogin pending = pendingLogins.remove(mfaToken);
        if (pending == null || pending.expiresAt().isBefore(Instant.now())) return null;
        return pending.username();
    }

    @Scheduled(fixedRate = 60_000)
    public void evictExpired() {
        Instant now = Instant.now();
        pendingLogins.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }

    private record PendingLogin(String username, Instant expiresAt) {}
}