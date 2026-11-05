
package com.repolens.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class OAuthStateManager {

    private static final int STATE_BYTES = 32;
    private static final long STATE_LIFETIME_SECONDS = 300;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;

    private final Map<String, Instant> states =
            new ConcurrentHashMap<>();

    public OAuthStateManager() {
        this(Clock.systemUTC());
    }

    OAuthStateManager(Clock clock) {
        this.clock = clock;
    }

    public String createState() {
        byte[] randomBytes = new byte[STATE_BYTES];
        secureRandom.nextBytes(randomBytes);

        String state = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        states.put(state, clock.instant());

        return state;
    }

    public boolean validateAndConsume(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }

        Instant createdAt = states.remove(state);

        if (createdAt == null) {
            return false;
        }

        return clock.instant().isBefore(
                createdAt.plusSeconds(STATE_LIFETIME_SECONDS)
        );
    }

    public int removeExpiredStates() {
        Instant now = clock.instant();
        AtomicInteger removed = new AtomicInteger();
        states.forEach((state, createdAt) -> {
            if (!now.isBefore(createdAt.plusSeconds(STATE_LIFETIME_SECONDS))
                    && states.remove(state, createdAt)) {
                removed.incrementAndGet();
            }
        });
        return removed.get();
    }
}
