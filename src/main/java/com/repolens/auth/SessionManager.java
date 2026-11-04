
package com.repolens.auth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private static final int SESSION_ID_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    private final Map<String, GitHubOAuthService.GitHubUser> sessions =
            new ConcurrentHashMap<>();

    public String createSession(
            GitHubOAuthService.GitHubUser user
    ) {
        byte[] randomBytes = new byte[SESSION_ID_BYTES];
        secureRandom.nextBytes(randomBytes);

        String sessionId = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        sessions.put(sessionId, user);

        return sessionId;
    }

    public GitHubOAuthService.GitHubUser getUser(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }

        return sessions.get(sessionId);
    }

    public void removeSession(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            sessions.remove(sessionId);
        }
    }

    public boolean isAuthenticated(String sessionId) {
        return getUser(sessionId) != null;
    }
}
