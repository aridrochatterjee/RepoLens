
package com.repolens.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;

public class GitHubApiClient {

    private static final String API_BASE_URL = "https://api.github.com";
    private static final String API_VERSION = "2022-11-28";
    private static final int MAX_REDIRECTS = 3;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GitHubApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        this.objectMapper = new ObjectMapper();
    }

    public JsonNode getRepository(
            String owner,
            String repository,
            String accessToken
    ) {
        URI endpoint = URI.create(
                API_BASE_URL + "/repos/" + owner + "/" + repository
        );

        return getJson(endpoint, accessToken);
    }

    public JsonNode getRepositoryLanguages(
            String owner,
            String repository,
            String accessToken
    ) {
        URI endpoint = URI.create(
                API_BASE_URL + "/repos/" + owner + "/" + repository
                        + "/languages"
        );

        return getJson(endpoint, accessToken);
    }

    public JsonNode getRepositoryCommitActivity(
            String owner,
            String repository,
            String accessToken
    ) {
        URI endpoint = URI.create(
                API_BASE_URL + "/repos/" + owner + "/" + repository
                        + "/stats/commit_activity"
        );

        return getJson(endpoint, accessToken);
    }

    public JsonNode getRepositoryCommits(
            String owner,
            String repository,
            String accessToken,
            int page,
            int limit
    ) {
        return getRepositoryCommits(
                owner, repository, accessToken, page, limit, null, null, null
        );
    }

    public JsonNode getRepositoryCommits(
            String owner,
            String repository,
            String accessToken,
            int page,
            int limit,
            String author,
            String since,
            String until
    ) {
        if ((author == null || author.isBlank())
                && (since == null || since.isBlank())
                && (until == null || until.isBlank())) {
            return getRepositoryCommits(
                    owner, repository, accessToken, page, limit
            );
        }
        StringBuilder endpoint = new StringBuilder(
                API_BASE_URL + "/repos/" + owner + "/" + repository
                        + "/commits?per_page=" + limit + "&page=" + page
        );
        appendQueryParameter(endpoint, "author", author);
        appendQueryParameter(endpoint, "since", since);
        appendQueryParameter(endpoint, "until", until);

        return getJson(URI.create(endpoint.toString()), accessToken);
    }

    public JsonNode getRepositoryContributors(
            String owner,
            String repository,
            String accessToken,
            int page,
            int limit
    ) {
        URI endpoint = URI.create(
                API_BASE_URL + "/repos/" + owner + "/" + repository
                        + "/contributors?per_page=" + limit + "&page=" + page
        );

        return getJson(endpoint, accessToken);
    }

    public JsonNode getRepositoryPullRequests(
            String owner,
            String repository,
            int page,
            int limit,
            String accessToken
    ) {
        URI endpoint = URI.create(
                API_BASE_URL + "/repos/" + owner + "/" + repository
                        + "/pulls?state=all&sort=updated&direction=desc"
                        + "&per_page=" + limit + "&page=" + page
        );
        return getJson(endpoint, accessToken);
    }

    public JsonNode getRepositoryIssues(
            String owner,
            String repository,
            int page,
            int limit,
            String accessToken
    ) {
        URI endpoint = URI.create(
                API_BASE_URL + "/repos/" + owner + "/" + repository
                        + "/issues?state=all&sort=updated&direction=desc"
                        + "&per_page=" + limit + "&page=" + page
        );
        return getJson(endpoint, accessToken);
    }

    public JsonNode getRepositoryCommitsByAuthor(
            String owner,
            String repository,
            String username,
            int page,
            int limit,
            String accessToken
    ) {
        String encodedUsername = URLEncoder.encode(
                username,
                StandardCharsets.UTF_8
        );
        URI endpoint = URI.create(
                API_BASE_URL + "/repos/" + owner + "/" + repository
                        + "/commits?author=" + encodedUsername
                        + "&per_page=" + limit + "&page=" + page
        );
        return getJson(endpoint, accessToken);
    }

    public JsonNode getUser(String username, String accessToken) {
        URI endpoint = URI.create(
                API_BASE_URL + "/users/" + URLEncoder.encode(
                        username,
                        StandardCharsets.UTF_8
                )
        );
        return getJson(endpoint, accessToken);
    }

    public JsonNode searchRepositoryIssues(
            String owner,
            String repository,
            String qualifier,
            String accessToken
    ) {
        String query = "repo:" + owner + "/" + repository + " " + qualifier;
        URI endpoint = URI.create(
                API_BASE_URL + "/search/issues?q="
                        + URLEncoder.encode(query, StandardCharsets.UTF_8)
                        + "&per_page=1"
        );
        return getJson(endpoint, accessToken);
    }

    public JsonNode getPullRequestReviews(
            String owner,
            String repository,
            long pullRequestNumber,
            String accessToken
    ) {
        URI endpoint = URI.create(
                API_BASE_URL + "/repos/" + owner + "/" + repository
                        + "/pulls/" + pullRequestNumber
                        + "/reviews?per_page=100&page=1"
        );
        return getJson(endpoint, accessToken);
    }

    public JsonNode getRepositoryTree(
            String owner,
            String repository,
            String branch,
            String accessToken
    ) {
        String encodedBranch = URLEncoder.encode(
                branch,
                StandardCharsets.UTF_8
        ).replace("+", "%20");
        URI endpoint = URI.create(
                API_BASE_URL + "/repos/" + owner + "/" + repository
                        + "/git/trees/" + encodedBranch + "?recursive=1"
        );

        return getJson(endpoint, accessToken);
    }

    private void appendQueryParameter(
            StringBuilder query,
            String name,
            String value
    ) {
        if (value == null || value.isBlank()) return;
        query.append('&')
                .append(name)
                .append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    public JsonNode getRepositoryFile(
            String owner,
            String repository,
            String path,
            String reference,
            String accessToken
    ) {
        String encodedPath = java.util.Arrays.stream(path.split("/", -1))
                .map(segment -> URLEncoder.encode(
                        segment,
                        StandardCharsets.UTF_8
                ).replace("+", "%20"))
                .collect(java.util.stream.Collectors.joining("/"));
        String encodedReference = URLEncoder.encode(
                reference,
                StandardCharsets.UTF_8
        ).replace("+", "%20");
        URI endpoint = URI.create(
                API_BASE_URL + "/repos/" + owner + "/" + repository
                        + "/contents/" + encodedPath + "?ref=" + encodedReference
        );
        return getJson(endpoint, accessToken);
    }

    private JsonNode getJson(URI endpoint, String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException(
                    "GitHub access token is required."
            );
        }

        for (int redirectCount = 0;
             redirectCount <= MAX_REDIRECTS;
             redirectCount++) {

            validateEndpoint(endpoint);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(endpoint)
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", API_VERSION)
                    .header("User-Agent", "RepoLens/1.0")
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

                int status = response.statusCode();

                if (status == 200) {
                    return objectMapper.readTree(response.body());
                }

                if (status == 202) {
                    throw new GitHubApiException(
                            "GitHub is still calculating repository activity. Try again shortly.",
                            202
                    );
                }

                if (status == 301 || status == 302
                        || status == 307 || status == 308) {

                    String location = response.headers()
                            .firstValue("Location")
                            .orElseThrow(() -> new GitHubApiException(
                                    "GitHub redirect did not include a location.",
                                    status
                            ));

                    if (redirectCount == MAX_REDIRECTS) {
                        throw new GitHubApiException(
                                "Too many GitHub redirects.",
                                502
                        );
                    }

                    endpoint = endpoint.resolve(location);
                    continue;
                }

                handleError(response);

                throw new GitHubApiException(
                        "Unexpected GitHub API response.",
                        status
                );

            } catch (IOException exception) {
                throw new GitHubApiException(
                        "Failed to communicate with GitHub.",
                        0,
                        exception
                );

            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();

                throw new GitHubApiException(
                        "GitHub API request was interrupted.",
                        0,
                        exception
                );
            }
        }

        throw new GitHubApiException(
                "Unable to retrieve GitHub data.",
                502
        );
    }

    private void validateEndpoint(URI endpoint) {
        if (!"https".equalsIgnoreCase(endpoint.getScheme())
                || !"api.github.com".equalsIgnoreCase(endpoint.getHost())
                || endpoint.getUserInfo() != null
                || endpoint.getPort() != -1) {

            throw new GitHubApiException(
                    "GitHub returned an unsafe redirect.",
                    502
            );
        }
    }

    private void handleError(HttpResponse<String> response) {
        int status = response.statusCode();

        String rateLimitRemaining = response.headers()
                .firstValue("X-RateLimit-Remaining")
                .orElse("");

        String retryAfter = response.headers()
                .firstValue("Retry-After")
                .orElse("");

        String message = switch (status) {
            case 401 -> "GitHub authentication failed. Please sign in again.";

            case 403 -> {
                if ("0".equals(rateLimitRemaining)) {
                    yield "GitHub API rate limit exceeded.";
                }

                yield "GitHub denied this request.";
            }

            case 404 -> "Repository not found or inaccessible.";
            case 429 -> "GitHub API rate limit exceeded.";
            case 422 -> "GitHub could not process this analytics request.";

            case 500, 502, 503, 504 ->
                    "GitHub is temporarily unavailable.";

            default ->
                    "GitHub API request failed with status " + status;
        };

        if (!retryAfter.isBlank()) {
            message += " Retry after: " + retryAfter + " seconds.";
        }

        throw new GitHubApiException(message, status);
    }
}
