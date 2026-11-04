
package com.repolens.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repolens.auth.GitHubOAuthService;
import com.repolens.auth.OAuthStateManager;
import com.repolens.auth.SessionManager;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class OAuthController {

    private static final String SESSION_COOKIE = "REPOLENS_SESSION";
    private static final String STATE_COOKIE = "REPOLENS_OAUTH_STATE";

    private final GitHubOAuthService oauthService;
    private final SessionManager sessionManager;
    private final OAuthStateManager stateManager;
    private final boolean secureCookies;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public OAuthController(
            GitHubOAuthService oauthService,
            SessionManager sessionManager,
            OAuthStateManager stateManager,
            boolean secureCookies
    ) {
        this.oauthService = oauthService;
        this.sessionManager = sessionManager;
        this.stateManager = stateManager;
        this.secureCookies = secureCookies;
    }

    public void login(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            sendError(exchange, 405, "Method not allowed.");
            return;
        }

        String state = stateManager.createState();

        exchange.getResponseHeaders().add(
                "Set-Cookie",
                buildCookie(
                        STATE_COOKIE,
                        state,
                        300
                )
        );

        String authorizationUrl =
                oauthService.buildAuthorizationUrl(state);

        redirect(exchange, authorizationUrl);
    }

    public void callback(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            sendError(exchange, 405, "Method not allowed.");
            return;
        }

        String query = exchange.getRequestURI().getRawQuery();

        String code = getQueryParameter(query, "code");
        String state = getQueryParameter(query, "state");

        String cookieState = getCookie(exchange, STATE_COOKIE);

        exchange.getResponseHeaders().add(
                "Set-Cookie",
                buildCookie(STATE_COOKIE, "", 0)
        );

        if (code == null || code.isBlank()
                || state == null || state.isBlank()
                || cookieState == null
                || !constantTimeEquals(state, cookieState)
                || !stateManager.validateAndConsume(state)) {

            sendError(exchange, 400, "Invalid or expired OAuth state.");
            return;
        }

        try {
            GitHubOAuthService.GitHubUser user =
                    oauthService.exchangeCode(code);

            String sessionId = sessionManager.createSession(user);

            exchange.getResponseHeaders().add(
                    "Set-Cookie",
                    buildCookie(
                            SESSION_COOKIE,
                            sessionId,
                            86400
                    )
            );

            redirect(exchange, "/?auth=success");

        } catch (Exception exception) {
            exception.printStackTrace();

            redirect(exchange, "/?auth=error");
        }
    }

    public void currentUser(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            sendError(exchange, 405, "Method not allowed.");
            return;
        }

        String sessionId = getSessionCookie(exchange);

        GitHubOAuthService.GitHubUser user =
                sessionManager.getUser(sessionId);

        if (user == null) {
            sendJson(exchange, 200, Map.of(
                    "authenticated", false
            ));
            return;
        }

        sendJson(exchange, 200, Map.of(
                "authenticated", true,
                "user", Map.of(
                        "id", user.id(),
                        "name", user.name(),
                        "username", user.username(),
                        "avatarUrl", user.avatarUrl()
                )
        ));
    }

    public void logout(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            sendError(exchange, 405, "Method not allowed.");
            return;
        }

        String sessionId = getSessionCookie(exchange);

        sessionManager.removeSession(sessionId);

        exchange.getResponseHeaders().add(
                "Set-Cookie",
                buildCookie(SESSION_COOKIE, "", 0)
        );

        sendJson(exchange, 200, Map.of(
                "success", true,
                "message", "Logged out successfully."
        ));
    }

    public String getSessionCookie(HttpExchange exchange) {
        return getCookie(exchange, SESSION_COOKIE);
    }

    private String getCookie(
            HttpExchange exchange,
            String cookieName
    ) {
        String cookieHeader = exchange.getRequestHeaders()
                .getFirst("Cookie");

        if (cookieHeader == null || cookieHeader.isBlank()) {
            return null;
        }

        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);

            if (parts.length == 2
                    && parts[0].trim().equals(cookieName)) {
                return parts[1].trim();
            }
        }

        return null;
    }

    private String buildCookie(
            String name,
            String value,
            int maxAge
    ) {
        StringBuilder cookie = new StringBuilder()
                .append(name)
                .append("=")
                .append(value)
                .append("; Path=/")
                .append("; HttpOnly")
                .append("; SameSite=Lax")
                .append("; Max-Age=")
                .append(maxAge);

        if (secureCookies) {
            cookie.append("; Secure");
        }

        return cookie.toString();
    }

    private String getQueryParameter(
            String query,
            String parameterName
    ) {
        if (query == null || query.isBlank()) {
            return null;
        }

        for (String parameter : query.split("&")) {
            String[] parts = parameter.split("=", 2);

            String key = URLDecoder.decode(
                    parts[0],
                    StandardCharsets.UTF_8
            );

            if (key.equals(parameterName)) {
                if (parts.length != 2) {
                    return null;
                }

                return URLDecoder.decode(
                        parts[1],
                        StandardCharsets.UTF_8
                );
            }
        }

        return null;
    }

    private boolean constantTimeEquals(String first, String second) {
        if (first == null || second == null) {
            return false;
        }

        return java.security.MessageDigest.isEqual(
                first.getBytes(StandardCharsets.UTF_8),
                second.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void redirect(
            HttpExchange exchange,
            String location
    ) throws IOException {
        exchange.getResponseHeaders().set(
                "Location",
                location
        );

        exchange.getResponseHeaders().set(
                "Cache-Control",
                "no-store"
        );

        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private void sendJson(
            HttpExchange exchange,
            int status,
            Object response
    ) throws IOException {
        byte[] body = objectMapper.writeValueAsBytes(response);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=UTF-8"
        );

        exchange.getResponseHeaders().set(
                "Cache-Control",
                "no-store"
        );

        exchange.getResponseHeaders().set(
                "X-Content-Type-Options",
                "nosniff"
        );

        exchange.sendResponseHeaders(status, body.length);

        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private void sendError(
            HttpExchange exchange,
            int status,
            String message
    ) throws IOException {
        sendJson(exchange, status, Map.of(
                "error", message
        ));
    }
}
