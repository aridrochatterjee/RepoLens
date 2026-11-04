
package com.repolens.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repolens.auth.GitHubOAuthService;
import com.repolens.client.GitHubApiException;
import com.repolens.model.Repository;
import com.repolens.service.RepositoryService;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class RepositoryController {

    private final RepositoryService repositoryService;
    private final ObjectMapper objectMapper;

    public RepositoryController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
        this.objectMapper = new ObjectMapper();
    }

    public void getRepository(
            HttpExchange exchange,
            GitHubOAuthService.GitHubUser user
    ) throws IOException {

        if (!exchange.getRequestMethod().equals("GET")) {
            sendError(exchange, 405, "Method not allowed.");
            return;
        }

        if (!exchange.getRequestURI().getPath().equals("/api/repository")) {
            sendError(exchange, 404, "Endpoint not found.");
            return;
        }

        if (!isAuthenticated(user)) {
            sendError(exchange, 401, "Authentication required.");
            return;
        }

        try {
            String repositoryUrl = extractRepositoryUrl(
                    exchange.getRequestURI().getRawQuery()
            );

            Repository repository = repositoryService.analyzeRepository(
                    repositoryUrl,
                    user.accessToken()
            );

            sendJson(exchange, 200, repository);

        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());

        } catch (GitHubApiException exception) {
            sendGitHubError(exchange, exception);

        } catch (Exception exception) {
            exception.printStackTrace();
            sendError(exchange, 500, "An internal server error occurred.");
        }
    }

    public void getRepositoryLanguages(
            HttpExchange exchange,
            GitHubOAuthService.GitHubUser user
    ) throws IOException {

        if (!exchange.getRequestMethod().equals("GET")) {
            sendError(exchange, 405, "Method not allowed.");
            return;
        }

        if (!exchange.getRequestURI().getPath()
                .equals("/api/repository/languages")) {
            sendError(exchange, 404, "Endpoint not found.");
            return;
        }

        if (!isAuthenticated(user)) {
            sendError(exchange, 401, "Authentication required.");
            return;
        }

        try {
            String repositoryUrl = extractRepositoryUrl(
                    exchange.getRequestURI().getRawQuery()
            );

            var analytics = repositoryService.analyzeLanguages(
                    repositoryUrl,
                    user.accessToken()
            );

            sendJson(exchange, 200, analytics);

        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());

        } catch (GitHubApiException exception) {
            sendGitHubError(exchange, exception);

        } catch (Exception exception) {
            exception.printStackTrace();
            sendError(exchange, 500, "An internal server error occurred.");
        }
    }

    private boolean isAuthenticated(
            GitHubOAuthService.GitHubUser user
    ) {
        return user != null
                && user.accessToken() != null
                && !user.accessToken().isBlank();
    }

    private String extractRepositoryUrl(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required parameter: url."
            );
        }

        String repositoryUrl = null;

        for (String parameter : rawQuery.split("&")) {
            String[] parts = parameter.split("=", 2);

            String key = URLDecoder.decode(
                    parts[0],
                    StandardCharsets.UTF_8
            );

            if (!key.equals("url")) {
                continue;
            }

            if (repositoryUrl != null) {
                throw new IllegalArgumentException(
                        "Only one repository URL is allowed."
                );
            }

            if (parts.length != 2 || parts[1].isBlank()) {
                throw new IllegalArgumentException(
                        "Repository URL cannot be empty."
                );
            }

            repositoryUrl = URLDecoder.decode(
                    parts[1],
                    StandardCharsets.UTF_8
            );
        }

        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required parameter: url."
            );
        }

        return repositoryUrl;
    }

    private void sendGitHubError(
            HttpExchange exchange,
            GitHubApiException exception
    ) throws IOException {
        sendError(
                exchange,
                mapGitHubStatus(exception),
                exception.getMessage()
        );
    }

    private int mapGitHubStatus(GitHubApiException exception) {
        return switch (exception.getStatusCode()) {
            case 401 -> 401;
            case 403 -> 403;
            case 404 -> 404;
            case 429 -> 429;
            default -> 502;
        };
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
                "X-Content-Type-Options",
                "nosniff"
        );

        exchange.getResponseHeaders().set(
                "Cache-Control",
                "no-store"
        );

        exchange.sendResponseHeaders(status, body.length);

        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private void sendError(
            HttpExchange exchange,
            int status,
            String error
    ) throws IOException {
        sendJson(exchange, status, new ErrorResponse(error));
    }

    private record ErrorResponse(String error) {}
}
