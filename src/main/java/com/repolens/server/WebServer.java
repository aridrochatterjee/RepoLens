
package com.repolens.server;

import com.repolens.auth.SessionManager;
import com.repolens.controller.OAuthController;
import com.repolens.controller.RepositoryController;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;

public class WebServer {

    private final int port;
    private final Path frontendDirectory;

    private final OAuthController oauthController;
    private final RepositoryController repositoryController;
    private final SessionManager sessionManager;

    private HttpServer server;

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
            OAuthController oauthController,
            RepositoryController repositoryController,
            SessionManager sessionManager
    ) {
        this.port = port;
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
                    new InetSocketAddress(port),
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
                    "/api/repository",
                    this::authenticatedRepository
            );

            server.createContext("/", this::serveStaticFiles);

            server.setExecutor(Executors.newFixedThreadPool(10));

            server.start();

            System.out.println(
                    "RepoLens running at http://localhost:" + port
            );

        } catch (IOException exception) {
            throw new RuntimeException(
                    "Failed to start RepoLens server",
                    exception
            );
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
}
