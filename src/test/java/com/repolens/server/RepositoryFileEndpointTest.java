package com.repolens.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repolens.auth.GitHubOAuthService;
import com.repolens.auth.OAuthStateManager;
import com.repolens.auth.SessionManager;
import com.repolens.client.GitHubApiClient;
import com.repolens.client.GitHubApiException;
import com.repolens.controller.OAuthController;
import com.repolens.controller.RepositoryController;
import com.repolens.service.CodebaseAnalysisService;
import com.repolens.service.RepositoryService;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepositoryFileEndpointTest {

    private static final String REPOSITORY_URL =
            "https://github.com/octocat/Hello-World";
    private static final String FILE_CONTENT =
            "class App { String label = \"café\"; }\n";

    private final ObjectMapper mapper = new ObjectMapper();
    private final FakeGitHubApiClient github = new FakeGitHubApiClient();
    private final SessionManager sessions = new SessionManager();
    private WebServer server;
    private String sessionId;

    @BeforeEach
    void startServer() throws Exception {
        github.repository = mapper.createObjectNode()
                .put("name", "Hello-World")
                .put("full_name", "octocat/Hello-World")
                .put("html_url", REPOSITORY_URL)
                .put("default_branch", "main");
        github.file = mapper.createObjectNode()
                .put("type", "file")
                .put("path", "frontend/js/app.js")
                .put("size", FILE_CONTENT.getBytes(StandardCharsets.UTF_8).length)
                .put("encoding", "base64")
                .put("content", Base64.getEncoder().encodeToString(
                        FILE_CONTENT.getBytes(StandardCharsets.UTF_8)
                ));
        sessionId = sessions.createSession(new GitHubOAuthService.GitHubUser(
                1L,
                "octocat",
                "Octo Cat",
                "",
                "server-only-token"
        ));
        RepositoryService repositoryService = new RepositoryService(github);
        RepositoryController repositoryController = new RepositoryController(
                repositoryService,
                new CodebaseAnalysisService(github, repositoryService)
        );
        server = new WebServer(
                0,
                InetAddress.getByName("127.0.0.1"),
                new OAuthController(null, sessions, new OAuthStateManager(), true),
                repositoryController,
                sessions
        );
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop();
    }

    @Test
    void servesUtf8FileContentThroughTheAuthenticatedRoute() throws Exception {
        HttpResponse<String> response = request(
                "/api/repository/file?url=" + encode(REPOSITORY_URL)
                        + "&path=frontend%2Fjs%2Fapp.js&ref=main",
                true
        );

        assertEquals(200, response.statusCode());
        JsonNode body = mapper.readTree(response.body());
        assertEquals("frontend/js/app.js", body.path("path").asText());
        assertEquals("app.js", body.path("name").asText());
        assertEquals("JavaScript", body.path("language").asText());
        assertEquals(FILE_CONTENT, body.path("content").asText());
        assertEquals("UTF-8", body.path("encoding").asText());
        assertEquals("main", github.reference);
        assertEquals("server-only-token", github.accessToken);
        assertEquals(-1, response.body().indexOf("server-only-token"));
    }

    @Test
    void reportsMissingFilesAndRejectsTraversalPaths() throws Exception {
        github.failure = new GitHubApiException(
                "Repository not found or inaccessible.",
                404
        );
        HttpResponse<String> missing = request(
                "/api/repository/file?url=" + encode(REPOSITORY_URL)
                        + "&path=missing.js&ref=main",
                true
        );
        assertEquals(404, missing.statusCode());
        assertEquals(
                "FILE_NOT_FOUND",
                mapper.readTree(missing.body()).path("code").asText()
        );

        int requestsBeforeInvalidPath = github.fileRequests;
        HttpResponse<String> invalid = request(
                "/api/repository/file?url=" + encode(REPOSITORY_URL)
                        + "&path=..%2Fsecret&ref=main",
                true
        );
        assertEquals(400, invalid.statusCode());
        assertEquals(
                "INVALID_REQUEST",
                mapper.readTree(invalid.body()).path("code").asText()
        );
        assertEquals(requestsBeforeInvalidPath, github.fileRequests);
    }

    @Test
    void rejectsUnauthenticatedFileRequests() throws Exception {
        HttpResponse<String> response = request(
                "/api/repository/file?url=" + encode(REPOSITORY_URL)
                        + "&path=frontend%2Fjs%2Fapp.js",
                false
        );

        assertEquals(401, response.statusCode());
        assertEquals(
                "Authentication required.",
                mapper.readTree(response.body()).path("error").asText()
        );
    }

    @Test
    void reportsWhenTheFileRouteDoesNotMatchExactly() throws Exception {
        HttpResponse<String> response = request(
                "/api/repository/file/?url=" + encode(REPOSITORY_URL)
                        + "&path=frontend%2Fjs%2Fapp.js",
                true
        );

        assertEquals(404, response.statusCode());
        assertEquals(
                "ENDPOINT_NOT_FOUND",
                mapper.readTree(response.body()).path("code").asText()
        );
    }

    @Test
    void appliesCommitAuthorAndDateFiltersThroughTheExistingRoute()
            throws Exception {
        HttpResponse<String> response = request(
                "/api/repository/commits?url=" + encode(REPOSITORY_URL)
                        + "&author=octocat&since=2026-03-01&until=2026-03-07"
                        + "&page=2&limit=30",
                true
        );

        assertEquals(200, response.statusCode());
        assertEquals(
                "octocat",
                mapper.readTree(response.body()).path("commits")
                        .get(0).path("author").asText()
        );
        assertEquals("octocat", github.commitAuthor);
        assertEquals("2026-03-01T00:00:00Z", github.commitSince);
        assertEquals("2026-03-07T23:59:59.999999999Z", github.commitUntil);
    }

    private HttpResponse<String> request(String path, boolean authenticated)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.getPort() + path))
                .GET();
        if (authenticated) {
            request.header("Cookie", "REPOLENS_SESSION=" + sessionId);
        }
        return HttpClient.newHttpClient().send(
                request.build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static final class FakeGitHubApiClient extends GitHubApiClient {
        private JsonNode repository;
        private JsonNode file;
        private GitHubApiException failure;
        private int fileRequests;
        private String reference;
        private String accessToken;
        private String commitAuthor;
        private String commitSince;
        private String commitUntil;

        @Override
        public JsonNode getRepository(String owner, String name, String token) {
            return repository;
        }

        @Override
        public JsonNode getRepositoryFile(
                String owner,
                String name,
                String path,
                String requestedReference,
                String token
        ) {
            fileRequests++;
            reference = requestedReference;
            accessToken = token;
            if (failure != null) throw failure;
            return file;
        }

        @Override
        public JsonNode getRepositoryCommits(
                String owner,
                String name,
                String token,
                int page,
                int limit,
                String author,
                String since,
                String until
        ) {
            commitAuthor = author;
            commitSince = since;
            commitUntil = until;
            var commits = new ObjectMapper().createArrayNode();
            var item = commits.addObject()
                    .put("sha", "commit-sha")
                    .put("html_url", "https://github.com/octocat/Hello-World/commit/commit-sha");
            item.putObject("commit")
                    .put("message", "Filtered commit")
                    .putObject("author")
                    .put("date", "2026-03-03T12:00:00Z")
                    .put("name", "Octocat");
            item.putObject("author").put("login", author);
            return commits;
        }
    }
}
