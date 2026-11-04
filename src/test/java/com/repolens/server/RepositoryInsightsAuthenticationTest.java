package com.repolens.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.repolens.auth.OAuthStateManager;
import com.repolens.auth.SessionManager;
import com.repolens.controller.OAuthController;
import com.repolens.controller.RepositoryController;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RepositoryInsightsAuthenticationTest {

    private WebServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop();
    }

    @Test
    void protectsEveryNewInsightsEndpointWithSessionAuthentication()
            throws Exception {
        SessionManager sessions = new SessionManager();
        server = new WebServer(
                0,
                InetAddress.getByName("127.0.0.1"),
                new OAuthController(
                        null,
                        sessions,
                        new OAuthStateManager(),
                        true
                ),
                new RepositoryController(null),
                sessions
        );
        server.start();

        HttpClient client = HttpClient.newHttpClient();
        for (String endpoint : new String[] {
                "/api/repository/health",
                "/api/repository/activity",
                "/api/repository/commits",
                "/api/repository/contributors",
                "/api/repository/contributors/octocat?url=https%3A%2F%2Fgithub.com%2Foctocat%2FHello-World",
                "/api/repository/pull-requests",
                "/api/repository/issues",
                "/api/repository/collaboration",
                "/api/repository/codebase",
                "/api/repository/file"
        }) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            "http://127.0.0.1:" + server.getPort() + endpoint
                    ))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(401, response.statusCode(), endpoint);
        }
    }
}
