
package com.repolens.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repolens.auth.GitHubOAuthService;
import com.repolens.client.GitHubApiException;
import com.repolens.model.Repository;
import com.repolens.service.RepositoryService;
import com.repolens.service.CodebaseAnalysisService;
import com.repolens.service.DeveloperIntelligenceService;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

public class RepositoryController {

    private final RepositoryService repositoryService;
    private final CodebaseAnalysisService codebaseAnalysisService;
    private final DeveloperIntelligenceService developerIntelligenceService;
    private final ObjectMapper objectMapper;

    public RepositoryController(RepositoryService repositoryService) {
        this(repositoryService, null, null);
    }

    public RepositoryController(
            RepositoryService repositoryService,
            CodebaseAnalysisService codebaseAnalysisService
    ) {
        this(repositoryService, codebaseAnalysisService, null);
    }

    public RepositoryController(
            RepositoryService repositoryService,
            CodebaseAnalysisService codebaseAnalysisService,
            DeveloperIntelligenceService developerIntelligenceService
    ) {
        this.repositoryService = repositoryService;
        this.codebaseAnalysisService = codebaseAnalysisService;
        this.developerIntelligenceService = developerIntelligenceService;
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
            sendError(exchange, 500, "An internal server error occurred.");
        }
    }

    public void getRepositoryHealth(
            HttpExchange exchange,
            GitHubOAuthService.GitHubUser user
    ) throws IOException {
        handleAnalytics(
                exchange,
                user,
                "/api/repository/health",
                query -> repositoryService.analyzeHealth(
                        requiredParameter(query, "url"),
                        user.accessToken()
                )
        );
    }

    public void getCodebaseAnalysis(
            HttpExchange exchange,
            GitHubOAuthService.GitHubUser user
    ) throws IOException {
        handleAnalytics(
                exchange,
                user,
                "/api/repository/codebase",
                query -> codebaseAnalysisService.analyze(
                        requiredParameter(query, "url"),
                        user.accessToken(),
                        parseLargestLimit(query.getOrDefault("limit", "10"))
                )
        );
    }

    public void getRepositoryFile(
            HttpExchange exchange,
            GitHubOAuthService.GitHubUser user
    ) throws IOException {
        handleAnalytics(
                exchange,
                user,
                "/api/repository/file",
                query -> codebaseAnalysisService.getFile(
                        requiredParameter(query, "url"),
                        requiredParameter(query, "path"),
                        query.get("ref"),
                        user.accessToken()
                )
        );
    }

    public void getRepositoryActivity(
            HttpExchange exchange,
            GitHubOAuthService.GitHubUser user
    ) throws IOException {
        handleAnalytics(
                exchange,
                user,
                "/api/repository/activity",
                query -> repositoryService.analyzeActivity(
                        requiredParameter(query, "url"),
                        user.accessToken(),
                        parsePeriod(query.getOrDefault("period", "30"))
                )
        );
    }

    public void getRepositoryCommits(
            HttpExchange exchange,
            GitHubOAuthService.GitHubUser user
    ) throws IOException {
        handleAnalytics(
                exchange,
                user,
                "/api/repository/commits",
                query -> {
                    Pagination pagination = parsePagination(query);
                    return repositoryService.analyzeCommits(
                            requiredParameter(query, "url"),
                            user.accessToken(),
                            pagination.page(),
                            pagination.limit()
                    );
                }
        );
    }

    public void getRepositoryContributors(
            HttpExchange exchange,
            GitHubOAuthService.GitHubUser user
    ) throws IOException {
        handleAnalytics(
                exchange,
                user,
                "/api/repository/contributors",
                query -> repositoryService.analyzeContributors(
                        requiredParameter(query, "url"),
                        user.accessToken()
                )
        );
    }

    public void getContributorProfile(
            HttpExchange exchange,
            GitHubOAuthService.GitHubUser user
    ) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String prefix = "/api/repository/contributors/";
        if (!path.startsWith(prefix) || path.substring(prefix.length()).contains("/")) {
            sendError(exchange, 404, "Endpoint not found.");
            return;
        }
        String username = path.substring(prefix.length());
        handleAnalytics(
                exchange,
                user,
                path,
                query -> developerIntelligenceService.analyzeContributor(
                        requiredParameter(query, "url"),
                        username,
                        user.accessToken(),
                        parseLargestLimit(query.getOrDefault("limit", "10"))
                )
        );
    }

    public void getPullRequestAnalytics(
            HttpExchange exchange,
            GitHubOAuthService.GitHubUser user
    ) throws IOException {
        handleAnalytics(
                exchange,
                user,
                "/api/repository/pull-requests",
                query -> developerIntelligenceService.analyzePullRequests(
                        requiredParameter(query, "url"),
                        user.accessToken(),
                        parsePeriod(query.getOrDefault("period", "30")),
                        parseAnalyticsPage(query.getOrDefault("page", "1")),
                        parseAnalyticsLimit(query.getOrDefault("limit", "100"))
                )
        );
    }

    public void getIssueAnalytics(
            HttpExchange exchange,
            GitHubOAuthService.GitHubUser user
    ) throws IOException {
        handleAnalytics(
                exchange,
                user,
                "/api/repository/issues",
                query -> developerIntelligenceService.analyzeIssues(
                        requiredParameter(query, "url"),
                        user.accessToken(),
                        parsePeriod(query.getOrDefault("period", "30")),
                        parseAnalyticsPage(query.getOrDefault("page", "1")),
                        parseAnalyticsLimit(query.getOrDefault("limit", "100"))
                )
        );
    }

    public void getCollaborationInsights(
            HttpExchange exchange,
            GitHubOAuthService.GitHubUser user
    ) throws IOException {
        handleAnalytics(
                exchange,
                user,
                "/api/repository/collaboration",
                query -> developerIntelligenceService.analyzeCollaboration(
                        requiredParameter(query, "url"),
                        user.accessToken()
                )
        );
    }

    private void handleAnalytics(
            HttpExchange exchange,
            GitHubOAuthService.GitHubUser user,
            String expectedPath,
            Function<Map<String, String>, Object> operation
    ) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            sendError(exchange, 405, "Method not allowed.");
            return;
        }
        if (!exchange.getRequestURI().getPath().equals(expectedPath)) {
            sendError(exchange, 404, "Endpoint not found.");
            return;
        }
        if (!isAuthenticated(user)) {
            sendError(exchange, 401, "Authentication required.");
            return;
        }

        try {
            Map<String, String> query = parseQuery(
                    exchange.getRequestURI().getRawQuery()
            );
            validateAllowedParameters(expectedPath, query);
            sendJson(
                    exchange,
                    200,
                    operation.apply(query)
            );
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (CodebaseAnalysisService.FileTooLargeException exception) {
            sendError(exchange, 413, exception.getMessage());
        } catch (GitHubApiException exception) {
            sendGitHubError(exchange, exception);
        } catch (RuntimeException exception) {
            sendError(exchange, 500, "An internal server error occurred.");
        }
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return parameters;
        }

        for (String parameter : rawQuery.split("&", -1)) {
            String[] parts = parameter.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2
                    ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                    : "";
            if (key.isBlank() || parameters.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException(
                        "Query parameters must have unique, non-empty names."
                );
            }
        }
        return parameters;
    }

    private String requiredParameter(
            Map<String, String> parameters,
            String name
    ) {
        String value = parameters.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required parameter: " + name + "."
            );
        }
        return value;
    }

    private int parsePeriod(String value) {
        try {
            int period = Integer.parseInt(value);
            if (period == 7 || period == 30 || period == 90) {
                return period;
            }
        } catch (NumberFormatException ignored) {
            // Report one consistent validation error below.
        }
        throw new IllegalArgumentException(
                "Period must be 7, 30, or 90 days."
        );
    }

    Pagination parsePagination(Map<String, String> query) {
        int page = parseInteger(query.getOrDefault("page", "1"), "page");
        int limit = parseInteger(query.getOrDefault("limit", "10"), "limit");
        if (page < 1 || page > 100) {
            throw new IllegalArgumentException(
                    "Page must be between 1 and 100."
            );
        }
        if (limit < 1 || limit > 30) {
            throw new IllegalArgumentException(
                    "Limit must be between 1 and 30."
            );
        }
        return new Pagination(page, limit);
    }

    int parseLargestLimit(String value) {
        int limit = parseInteger(value, "limit");
        if (limit < 1 || limit > 30) {
            throw new IllegalArgumentException(
                    "Limit must be between 1 and 30."
            );
        }
        return limit;
    }

    int parseAnalyticsPage(String value) {
        int page = parseInteger(value, "page");
        if (page < 1 || page > 10) {
            throw new IllegalArgumentException(
                    "Page must be between 1 and 10."
            );
        }
        return page;
    }

    int parseAnalyticsLimit(String value) {
        int limit = parseInteger(value, "limit");
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                    "Limit must be between 1 and 100."
            );
        }
        return limit;
    }

    private int parseInteger(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    name + " must be a whole number."
            );
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
        Map<String, String> query = parseQuery(rawQuery);
        if (!Set.of("url").containsAll(query.keySet())) {
            throw new IllegalArgumentException(
                    "Unsupported query parameter."
            );
        }
        return requiredParameter(query, "url");
    }

    private void validateAllowedParameters(
            String path,
            Map<String, String> query
    ) {
        Set<String> allowed;
        if (path.startsWith("/api/repository/contributors/")) {
            allowed = Set.of("url", "limit");
        } else {
            allowed = switch (path) {
                case "/api/repository/activity" -> Set.of("url", "period");
                case "/api/repository/commits" -> Set.of("url", "page", "limit");
                case "/api/repository/codebase" -> Set.of("url", "limit");
                case "/api/repository/file" -> Set.of("url", "path", "ref");
                case "/api/repository/pull-requests",
                     "/api/repository/issues" -> Set.of(
                             "url", "period", "page", "limit"
                     );
                case "/api/repository/collaboration" -> Set.of("url");
                default -> Set.of("url");
            };
        }
        if (!allowed.containsAll(query.keySet())) {
            throw new IllegalArgumentException(
                    "Unsupported query parameter."
            );
        }
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

    int mapGitHubStatus(GitHubApiException exception) {
        return switch (exception.getStatusCode()) {
            case 401 -> 401;
            case 403 -> 403;
            case 404 -> 404;
            case 429 -> 429;
            case 202 -> 503;
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

    record Pagination(int page, int limit) {}
}
