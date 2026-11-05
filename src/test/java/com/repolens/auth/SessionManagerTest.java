package com.repolens.auth;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SessionManagerTest {

    @Test
    void expiresServerSideSessionsWhenTheirLifetimeEnds() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        SessionManager manager = new SessionManager(clock, Duration.ofHours(24));
        var user = new GitHubOAuthService.GitHubUser(
                1,
                "octocat",
                "Octocat",
                "",
                "server-side-token"
        );
        String sessionId = manager.createSession(user);

        assertNotNull(manager.getUser(sessionId));
        clock.setInstant(Instant.parse("2026-01-02T00:00:00Z"));
        assertNull(manager.getUser(sessionId));
    }

    @Test
    void removesInactiveExpiredSessionsAndPreservesActiveSessions() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        SessionManager manager = new SessionManager(clock, Duration.ofHours(1));
        var user = user();
        String expiredSession = manager.createSession(user);

        clock.setInstant(Instant.parse("2026-01-01T01:00:00Z"));
        String activeSession = manager.createSession(user);

        assertEquals(1, manager.removeExpiredSessions());
        assertNull(manager.getUser(expiredSession));
        assertNotNull(manager.getUser(activeSession));
        assertEquals(0, manager.removeExpiredSessions());
    }

    @Test
    void logoutRemovesOnlyTheRequestedSession() {
        SessionManager manager = new SessionManager(
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofHours(1)
        );
        String loggedOut = manager.createSession(user());
        String retained = manager.createSession(user());

        manager.removeSession(loggedOut);

        assertNull(manager.getUser(loggedOut));
        assertNotNull(manager.getUser(retained));
    }

    @Test
    void concurrentCreationAndCleanupPreserveNewSessions() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        SessionManager manager = new SessionManager(clock, Duration.ofHours(1));
        var sessionIds = new java.util.concurrent.ConcurrentLinkedQueue<String>();
        var start = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(5);
        try {
            var creators = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int worker = 0; worker < 4; worker++) {
                creators.add(executor.submit(() -> {
                    start.await();
                    for (int index = 0; index < 250; index++) {
                        sessionIds.add(manager.createSession(user()));
                    }
                    return null;
                }));
            }
            var cleanup = executor.submit(() -> {
                start.await();
                for (int index = 0; index < 1000; index++) {
                    manager.removeExpiredSessions();
                }
                return null;
            });
            start.countDown();
            for (var creator : creators) creator.get();
            cleanup.get();
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1000, sessionIds.size());
        for (String sessionId : sessionIds) {
            assertNotNull(manager.getUser(sessionId));
        }
    }

    private GitHubOAuthService.GitHubUser user() {
        return new GitHubOAuthService.GitHubUser(
                1,
                "octocat",
                "Octocat",
                "",
                "server-side-token"
        );
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
