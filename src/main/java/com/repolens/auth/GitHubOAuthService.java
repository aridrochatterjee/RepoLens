
package com.repolens.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class GitHubOAuthService {

    private static final String AUTHORIZE_URL =
            "https://github.com/login/oauth/authorize";

    private static final String TOKEN_URL =
            "https://github.com/login/oauth/access_token";

    private static final String USER_URL =
            "https://api.github.com/user";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GitHubOAuthService() {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        this.clientId = requireConfig(
                dotenv.get("GITHUB_CLIENT_ID"),
                "GITHUB_CLIENT_ID"
        );

        this.clientSecret = requireConfig(
                dotenv.get("GITHUB_CLIENT_SECRET"),
                "GITHUB_CLIENT_SECRET"
        );

        this.redirectUri = requireConfig(
                dotenv.get("GITHUB_REDIRECT_URI"),
                "GITHUB_REDIRECT_URI"
        );

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        this.objectMapper = new ObjectMapper();
    }

    public String buildAuthorizationUrl(String state) {
        return AUTHORIZE_URL
                + "?client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&scope=" + encode("read:user")
                + "&state=" + encode(state);
    }

    public GitHubUser exchangeCode(String code) {
        try {
            String requestBody =
                    "client_id=" + encode(clientId)
                    + "&client_secret=" + encode(clientSecret)
                    + "&code=" + encode(code)
                    + "&redirect_uri=" + encode(redirectUri);

            HttpRequest tokenRequest = HttpRequest.newBuilder()
                    .uri(URI.create(TOKEN_URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header(
                            "Content-Type",
                            "application/x-www-form-urlencoded"
                    )
                    .header("User-Agent", "RepoLens/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> tokenResponse = httpClient.send(
                    tokenRequest,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (tokenResponse.statusCode() != 200) {
                throw new IllegalStateException(
                        "GitHub token exchange failed."
                );
            }

            JsonNode tokenJson =
                    objectMapper.readTree(tokenResponse.body());

            String accessToken =
                    tokenJson.path("access_token").asText("");

            if (accessToken.isBlank() || tokenJson.has("error")) {
                throw new IllegalStateException(
                        "GitHub did not return a valid access token."
                );
            }

            return fetchUser(accessToken);

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to communicate with GitHub.",
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "GitHub authentication was interrupted.",
                    exception
            );
        }
    }

    private GitHubUser fetchUser(String accessToken)
            throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(USER_URL))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + accessToken)
                .header("User-Agent", "RepoLens/1.0")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Unable to retrieve the GitHub user profile."
            );
        }

        JsonNode user = objectMapper.readTree(response.body());

        return new GitHubUser(
                user.path("id").asLong(),
                user.path("login").asText(),
                user.path("name").asText(""),
                user.path("avatar_url").asText(""),
                accessToken
        );
    }

    private String requireConfig(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required environment variable: " + key
            );
        }

        return value;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record GitHubUser(
            long id,
            String username,
            String name,
            String avatarUrl,
            String accessToken
    ) {}
}
