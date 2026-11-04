package com.repolens.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.repolens.client.GitHubApiClient;
import com.repolens.client.GitHubApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepositoryInsightsServiceTest {

    private static final String REPOSITORY_URL =
            "https://github.com/octocat/Hello-World";

    private final ObjectMapper mapper = new ObjectMapper();
    private FakeGitHubApiClient github;
    private RepositoryService service;

    @BeforeEach
    void setUp() {
        github = new FakeGitHubApiClient();
        service = new RepositoryService(github);
        github.repository = repositoryResponse();
        github.activity = activityResponse(30, 2);
        github.contributors = contributorResponse();
        github.commits = commitResponse();
    }

    @Test
    void aggregatesDailyAndWeeklyActivityForTheSelectedWindow() {
        var activity = service.analyzeActivity(
                REPOSITORY_URL,
                "test-token",
                30
        );

        assertTrue(activity.available());
        assertEquals(30, activity.dailyActivity().size());
        assertEquals(60, activity.totalCommits());
        assertEquals(14.0, activity.averageCommitsPerWeek());
        assertEquals(5, activity.weeklyActivity().size());
        assertTrue(activity.mostActiveDay() != null);
    }

    @Test
    void reportsTiedMostActiveDaysWithoutChoosingArbitrarily() {
        github.activity = activityResponse(7, 1);

        var activity = service.analyzeActivity(
                REPOSITORY_URL,
                "test-token",
                7
        );

        assertEquals("Multiple days", activity.mostActiveDay());
    }

    @Test
    void treatsGitHubCalculatingActivityAsUnavailable() {
        github.activityFailure = new GitHubApiException(
                "Statistics are being calculated.",
                202
        );

        var activity = service.analyzeActivity(
                REPOSITORY_URL,
                "test-token",
                30
        );

        assertFalse(activity.available());
        assertNull(activity.totalCommits());
        assertNull(activity.averageCommitsPerWeek());
        assertTrue(activity.dailyActivity().isEmpty());
    }

    @Test
    void treatsEmptyActivityPayloadAsUnavailableInsteadOfZeroCommits() {
        github.activity = mapper.createArrayNode();

        var activity = service.analyzeActivity(
                REPOSITORY_URL,
                "test-token",
                30
        );

        assertFalse(activity.available());
        assertNull(activity.totalCommits());
        assertTrue(activity.unavailableReason().contains("no usable"));
    }

    @Test
    void healthScoreOmitsUnavailableFactorsInsteadOfScoringThemAsZero() {
        github.activityFailure = new GitHubApiException(
                "Statistics are being calculated.",
                202
        );

        var health = service.analyzeHealth(REPOSITORY_URL, "test-token");

        assertEquals(70, health.score());
        assertEquals(40, health.availableWeight());
        assertNull(health.factors().get(0).score());
        assertNull(health.factors().get(2).score());
        assertNull(health.factors().get(4).score());
        assertEquals(5, health.factors().size());
    }

    @Test
    void healthScoreIsUnavailableWhenAllSignalsAreMissing() {
        github.repository = mapper.createObjectNode()
                .put("id", 1)
                .put("full_name", "octocat/Hello-World");
        github.activityFailure = new GitHubApiException(
                "Statistics are being calculated.",
                202
        );
        github.contributorsFailure = new GitHubApiException(
                "Statistics are being calculated.",
                202
        );

        var health = service.analyzeHealth(REPOSITORY_URL, "test-token");

        assertNull(health.score());
        assertEquals(0, health.availableWeight());
        assertNull(health.factors().get(0).score());
        assertNull(health.factors().get(1).score());
        assertNull(health.factors().get(2).score());
        assertNull(health.factors().get(3).score());
        assertNull(health.factors().get(4).score());
    }

    @Test
    void mapsCommitFieldsAndUsesCommitAuthorWhenGitHubUserIsMissing() {
        var commits = service.analyzeCommits(
                REPOSITORY_URL,
                "test-token",
                1,
                10
        );

        assertEquals(1, commits.commits().size());
        assertEquals("0123456", commits.commits().get(0).shortSha());
        assertEquals("octocat", commits.commits().get(0).author());
        assertEquals(
                "https://github.com/octocat/Hello-World/commit/"
                        + "0123456789abcdef0123456789abcdef01234567",
                commits.commits().get(0).htmlUrl()
        );
    }

    @Test
    void sortsContributorsAndHandlesDeletedAccounts() {
        var contributors = service.analyzeContributors(
                REPOSITORY_URL,
                "test-token"
        );

        assertTrue(contributors.available());
        assertEquals(2, contributors.totalReturned());
        assertEquals("octocat", contributors.contributors().get(0).login());
        assertEquals(8, contributors.contributors().get(0).contributions());
        assertNull(contributors.contributors().get(1).login());
        assertEquals(20.0, contributors.contributors().get(1).contributionShare());
    }

    private JsonNode repositoryResponse() {
        ObjectNode repository = mapper.createObjectNode();
        repository.put("id", 1);
        repository.put("name", "Hello-World");
        repository.put("full_name", "octocat/Hello-World");
        repository.put("pushed_at", Instant.now().toString());
        repository.put("open_issues_count", 0);
        return repository;
    }

    private JsonNode activityResponse(int periodDays, int commitsPerDay) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate firstDay = today.minusDays(periodDays - 1L);
        Map<LocalDate, int[]> weeks = new LinkedHashMap<>();
        for (int offset = 0; offset < periodDays; offset++) {
            LocalDate date = firstDay.plusDays(offset);
            LocalDate sunday = date.minusDays(
                    date.getDayOfWeek().getValue() % 7
            );
            int[] days = weeks.computeIfAbsent(sunday, ignored -> new int[7]);
            int dayIndex = (int) (date.toEpochDay() - sunday.toEpochDay());
            days[dayIndex] = commitsPerDay;
        }

        ArrayNode activity = mapper.createArrayNode();
        weeks.forEach((sunday, dayCounts) -> {
            ObjectNode week = activity.addObject();
            week.put("week", sunday.atStartOfDay().toEpochSecond(ZoneOffset.UTC));
            ArrayNode days = week.putArray("days");
            for (int count : dayCounts) days.add(count);
        });
        return activity;
    }

    private JsonNode contributorResponse() {
        ArrayNode contributors = mapper.createArrayNode();
        ObjectNode first = contributors.addObject();
        first.put("contributions", 8);
        first.putObject("author")
                .put("login", "octocat")
                .put("avatar_url", "https://avatars.githubusercontent.com/u/1")
                .put("html_url", "https://github.com/octocat");
        ObjectNode deleted = contributors.addObject();
        deleted.put("contributions", 2);
        deleted.putNull("author");
        return contributors;
    }

    private JsonNode commitResponse() {
        ArrayNode commits = mapper.createArrayNode();
        ObjectNode item = commits.addObject();
        item.put("sha", "0123456789abcdef0123456789abcdef01234567");
        item.put("html_url",
                "https://github.com/octocat/Hello-World/commit/"
                        + "0123456789abcdef0123456789abcdef01234567");
        ObjectNode commit = item.putObject("commit");
        commit.put("message", "Initial commit\nDetails");
        commit.putObject("author")
                .put("name", "octocat")
                .put("date", Instant.now().toString());
        item.putNull("author");
        return commits;
    }

    private final class FakeGitHubApiClient extends GitHubApiClient {
        private JsonNode repository;
        private JsonNode activity;
        private JsonNode commits;
        private JsonNode contributors;
        private GitHubApiException activityFailure;
        private GitHubApiException contributorsFailure;

        @Override
        public JsonNode getRepository(String owner, String name, String token) {
            return repository;
        }

        @Override
        public JsonNode getRepositoryCommitActivity(
                String owner,
                String name,
                String token
        ) {
            if (activityFailure != null) throw activityFailure;
            return activity;
        }

        @Override
        public JsonNode getRepositoryCommits(
                String owner,
                String name,
                String token,
                int page,
                int limit
        ) {
            return commits;
        }

        @Override
        public JsonNode getRepositoryContributors(
                String owner,
                String name,
                String token,
                int page,
                int limit
        ) {
            if (contributorsFailure != null) throw contributorsFailure;
            return contributors;
        }
    }
}
