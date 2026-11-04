
package com.repolens.server;

import com.repolens.auth.SessionManager;
import com.repolens.controller.OAuthController;
import com.repolens.controller.RepositoryController;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebServer {

    private final int port;
    private final InetAddress bindAddress;
    private final Path frontendDirectory;

    private final OAuthController oauthController;
    private final RepositoryController repositoryController;
    private final SessionManager sessionManager;

    private HttpServer server;
    private ExecutorService executor;

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            ".html", "text/html; charset=UTF-8",
            ".css", "text/css; charset=UTF-8",
            ".js", "application/javascript; charset=UTF-8",
            ".json", "application/json; charset=UTF-8",
            ".svg", "image/svg+xml",
            ".png", "image/png",
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".ico", "image/x-icon"
    );

    public WebServer(
            int port,
            InetAddress bindAddress,
            OAuthController oauthController,
            RepositoryController repositoryController,
            SessionManager sessionManager
    ) {
        this.port = port;
        this.bindAddress = bindAddress;
        this.oauthController = oauthController;
        this.repositoryController = repositoryController;
        this.sessionManager = sessionManager;

        this.frontendDirectory = Path.of("frontend")
                .toAbsolutePath()
                .normalize();
    }

    public void start() {
        try {
            server = HttpServer.create(
                    new InetSocketAddress(bindAddress, port),
                    0
            );

            server.createContext("/api/health", this::healthCheck);

            server.createContext(
                    "/auth/github/login",
                    oauthController::login
            );

            server.createContext(
                    "/auth/github/callback",
                    oauthController::callback
            );

            server.createContext(
                    "/api/auth/me",
                    oauthController::currentUser
            );

            server.createContext(
                    "/api/auth/logout",
                    oauthController::logout
            );

            server.createContext(
                    "/api/repository/languages",
                    this::authenticatedRepositoryLanguages
            );

            server.createContext(
                    "/api/repository/health",
                    exchange -> authenticatedRepositoryInsight(
                            exchange,
                            repositoryController::getRepositoryHealth
                    )
            );

            server.createContext(
                    "/api/repository/activity",
                    exchange -> authenticatedRepositoryInsight(
                            exchange,
                            repositoryController::getRepositoryActivity
                    )
            );

            server.createContext(
                    "/api/repository/commits",
                    exchange -> authenticatedRepositoryInsight(
                            exchange,
                            repositoryController::getRepositoryCommits
                    )
            );

            server.createContext(
                    "/api/repository/contributors",
                    exchange -> authenticatedRepositoryInsight(
                            exchange,
                            repositoryController::getRepositoryContributors
                    )
            );

            server.createContext(
                    "/api/repository/codebase",
                    exchange -> authenticatedRepositoryInsight(
                            exchange,
                            repositoryController::getCodebaseAnalysis
                    )
            );

            server.createContext(
                    "/api/repository",
                    this::authenticatedRepository
            );

            server.createContext("/", this::serveStaticFiles);

            executor = Executors.newFixedThreadPool(10);
            server.setExecutor(executor);

            server.start();

            System.out.println(
                    "RepoLens running at http://localhost:"
                            + server.getAddress().getPort()
            );

        } catch (IOException exception) {
            throw new RuntimeException(
                    "Failed to start RepoLens server",
                    exception
            );
        }
    }

    public int getPort() {
        if (server == null) {
            throw new IllegalStateException("Server has not been started.");
        }
        return server.getAddress().getPort();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void authenticatedRepository(HttpExchange exchange)
            throws IOException {

        var user = getAuthenticatedUser(exchange);

        if (user == null) {
            sendResponse(
                    exchange,
                    401,
                    "{\"error\":\"Authentication required.\"}",
                    "application/json; charset=UTF-8"
            );
            return;
        }

        repositoryController.getRepository(exchange, user);
    }

    private void authenticatedRepositoryLanguages(
            HttpExchange exchange
    ) throws IOException {

        var user = getAuthenticatedUser(exchange);

        if (user == null) {
            sendResponse(
                    exchange,
                    401,
                    "{\"error\":\"Authentication required.\"}",
                    "application/json; charset=UTF-8"
            );
            return;
        }

        repositoryController.getRepositoryLanguages(exchange, user);
    }

    private void authenticatedRepositoryInsight(
            HttpExchange exchange,
            RepositoryInsightHandler handler
    ) throws IOException {
        var user = getAuthenticatedUser(exchange);
        if (user == null) {
            sendResponse(
                    exchange,
                    401,
                    "{\"error\":\"Authentication required.\"}",
                    "application/json; charset=UTF-8"
            );
            return;
        }
        handler.handle(exchange, user);
    }

    private com.repolens.auth.GitHubOAuthService.GitHubUser
    getAuthenticatedUser(HttpExchange exchange) {

        String sessionId = oauthController.getSessionCookie(exchange);

        return sessionManager.getUser(sessionId);
    }

    private void healthCheck(HttpExchange exchange)
            throws IOException {

        if (!exchange.getRequestMethod().equals("GET")) {
            sendResponse(
                    exchange,
                    405,
                    "Method Not Allowed",
                    "text/plain; charset=UTF-8"
            );
            return;
        }

        sendResponse(
                exchange,
                200,
                "{\"status\":\"UP\",\"application\":\"RepoLens\"}",
                "application/json; charset=UTF-8"
        );
    }

    private void serveStaticFiles(HttpExchange exchange)
            throws IOException {

        String method = exchange.getRequestMethod();

        if (!method.equals("GET") && !method.equals("HEAD")) {
            sendResponse(
                    exchange,
                    405,
                    "Method Not Allowed",
                    "text/plain; charset=UTF-8"
            );
            return;
        }

        String requestedPath = exchange.getRequestURI().getPath();

        if (requestedPath.equals("/")) {
            requestedPath = "/index.html";
        }

        Path file = frontendDirectory
                .resolve(requestedPath.substring(1))
                .normalize();

        if (!file.startsWith(frontendDirectory)) {
            sendResponse(
                    exchange,
                    403,
                    "Forbidden",
                    "text/plain; charset=UTF-8"
            );
            return;
        }

        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            sendResponse(
                    exchange,
                    404,
                    "404 - Not Found",
                    "text/plain; charset=UTF-8"
            );
            return;
        }

        String filename = file.getFileName().toString();

        String extension = filename.contains(".")
                ? filename.substring(filename.lastIndexOf("."))
                : "";

        String contentType = CONTENT_TYPES.getOrDefault(
                extension,
                "application/octet-stream"
        );

        byte[] content = Files.readAllBytes(file);

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");

        if (method.equals("HEAD")) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }

        exchange.sendResponseHeaders(200, content.length);

        try (var output = exchange.getResponseBody()) {
            output.write(content);
        }
    }

    private void sendResponse(
            HttpExchange exchange,
            int status,
            String response,
            String contentType
    ) throws IOException {

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");

        exchange.sendResponseHeaders(status, bytes.length);

        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @FunctionalInterface
    private interface RepositoryInsightHandler {
        void handle(
                HttpExchange exchange,
                com.repolens.auth.GitHubOAuthService.GitHubUser user
        ) throws IOException;
    }
}
