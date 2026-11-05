
package com.repolens.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SessionManager {

    private static final int SESSION_ID_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;
    private final Duration sessionLifetime;

    private final Map<String, Session> sessions =
            new ConcurrentHashMap<>();

    public SessionManager() {
        this(Clock.systemUTC(), Duration.ofHours(24));
    }

    SessionManager(Clock clock, Duration sessionLifetime) {
        this.clock = clock;
        this.sessionLifetime = sessionLifetime;
    }

    public String createSession(
            GitHubOAuthService.GitHubUser user
    ) {
        byte[] randomBytes = new byte[SESSION_ID_BYTES];
        secureRandom.nextBytes(randomBytes);

        String sessionId = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        sessions.put(
                sessionId,
                new Session(user, clock.instant().plus(sessionLifetime))
        );

        return sessionId;
    }

    public GitHubOAuthService.GitHubUser getUser(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }

        Session session = sessions.get(sessionId);
        if (session == null) {
            return null;
        }

        if (!clock.instant().isBefore(session.expiresAt())) {
            sessions.remove(sessionId, session);
            return null;
        }

        return session.user();
    }

    public void removeSession(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            sessions.remove(sessionId);
        }
    }

    public int removeExpiredSessions() {
        Instant now = clock.instant();
        AtomicInteger removed = new AtomicInteger();
        sessions.forEach((sessionId, observedSession) -> {
            if (!now.isBefore(observedSession.expiresAt())) {
                sessions.computeIfPresent(sessionId, (ignored, currentSession) -> {
                    if (currentSession == observedSession
                            && !now.isBefore(currentSession.expiresAt())) {
                        removed.incrementAndGet();
                        return null;
                    }
                    return currentSession;
                });
            }
        });
        return removed.get();
    }

    public boolean isAuthenticated(String sessionId) {
        return getUser(sessionId) != null;
    }

    private record Session(
            GitHubOAuthService.GitHubUser user,
            Instant expiresAt
    ) {}
}
