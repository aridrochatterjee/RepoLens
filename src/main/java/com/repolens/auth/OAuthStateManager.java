
package com.repolens.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OAuthStateManager {

    private static final int STATE_BYTES = 32;
    private static final long STATE_LIFETIME_SECONDS = 300;

    private final SecureRandom secureRandom = new SecureRandom();

    private final Map<String, Instant> states =
            new ConcurrentHashMap<>();

    public String createState() {
        byte[] randomBytes = new byte[STATE_BYTES];
        secureRandom.nextBytes(randomBytes);

        String state = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        states.put(state, Instant.now());

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

        return Instant.now().isBefore(
                createdAt.plusSeconds(STATE_LIFETIME_SECONDS)
        );
    }

    public void removeExpiredStates() {
        Instant expirationTime = Instant.now()
                .minusSeconds(STATE_LIFETIME_SECONDS);

        states.entrySet().removeIf(
                entry -> entry.getValue().isBefore(expirationTime)
        );
    }
}
