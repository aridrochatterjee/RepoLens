package com.repolens.server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class WebServer {

    private final int port;
    private HttpServer server;

    public WebServer(int port) {
        this.port = port;
    }

    public void start() {

        try {

            server = HttpServer.create(
                new InetSocketAddress(port),
                0
            );

            server.createContext("/api/health", this::healthCheck);

            server.createContext("/", exchange -> {

                String response = """
                    <h1>RepoLens Server Running!</h1>
                    <p>Core Java HTTP Server is working.</p>
                    """;

                sendResponse(exchange, 200, response);

            });

            server.setExecutor(
                java.util.concurrent.Executors.newFixedThreadPool(10)
            );

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

    private void healthCheck(HttpExchange exchange)
            throws IOException {

        String response = """
            {"status":"UP","application":"RepoLens"}
            """;

        exchange.getResponseHeaders().set(
            "Content-Type",
            "application/json; charset=UTF-8"
        );

        sendResponse(exchange, 200, response);

    }

    private void sendResponse(
            HttpExchange exchange,
            int status,
            String response
    ) throws IOException {

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(status, bytes.length);

        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }

    }

}