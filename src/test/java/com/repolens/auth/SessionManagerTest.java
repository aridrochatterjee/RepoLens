package com.repolens.auth;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
