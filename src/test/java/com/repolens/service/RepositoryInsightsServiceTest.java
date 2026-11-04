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
    void aggregatesFiftyTwoWeeksOfCompleteActivityForContributionHeatmaps() {
        github.activity = activityResponse(364, 1);

        var activity = service.analyzeActivity(
                REPOSITORY_URL,
                "test-token",
                364
        );

        assertTrue(activity.available());
        assertEquals(364, activity.dailyActivity().size());
        assertEquals(364, activity.totalCommits());
        assertTrue(activity.weeklyActivity().size() >= 52);
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

        assertEquals(60, health.score());
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
    void appliesValidatedAuthorAndInclusiveDateFiltersToCommitRequests() {
        service.analyzeCommits(
                REPOSITORY_URL,
                "test-token",
                2,
                30,
                "octocat",
                "2026-03-01",
                "2026-03-07"
        );

        assertEquals("octocat", github.commitAuthor);
        assertEquals("2026-03-01T00:00:00Z", github.commitSince);
        assertEquals("2026-03-07T23:59:59.999999999Z", github.commitUntil);
        assertEquals(2, github.commitPage);
        assertEquals(30, github.commitLimit);
    }

    @Test
    void rejectsInvalidCommitFiltersBeforeCallingGitHub() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.analyzeCommits(
                        REPOSITORY_URL,
                        "test-token",
                        1,
                        30,
                        "bad/name",
                        null,
                        null
                )
        );
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.analyzeCommits(
                        REPOSITORY_URL,
                        "test-token",
                        1,
                        30,
                        null,
                        "2026-03-08",
                        "2026-03-07"
                )
        );
        assertEquals(0, github.commitRequests);
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
        assertEquals(10L, contributors.totalContributionsReturned());
        assertEquals(2L, contributors.anonymousContributions());
        assertEquals(80.0, contributors.contributors().getFirst().contributionShare());
    }

    @Test
    void separatesAnonymousRowsAndUsesAllReturnedContributionsAsShareDenominator() {
        ArrayNode records = mapper.createArrayNode();
        records.addObject().put("contributions", 8).putObject("author")
                .put("login", "octocat")
                .put("avatar_url", "https://avatars.githubusercontent.com/u/1")
                .put("html_url", "https://github.com/octocat");
        records.addObject().put("contributions", 2).putNull("author");
        records.addObject().put("contributions", 3).putObject("author");
        records.addObject().put("contributions", 7).putObject("author")
                .putNull("login");
        records.addObject().put("contributions", 5).putObject("author")
                .put("login", "   ");
        github.contributors = records;

        var contributors = service.analyzeContributors(
                REPOSITORY_URL,
                "test-token"
        );

        assertEquals(1, contributors.contributors().size());
        assertEquals("octocat", contributors.contributors().getFirst().login());
        assertEquals(25L, contributors.totalContributionsReturned());
        assertEquals(17L, contributors.anonymousContributions());
        assertEquals(32.0, contributors.contributors().getFirst().contributionShare());
    }

    @Test
    void deduplicatesNamedContributorsAcrossBoundedPages() {
        ArrayNode duplicated = mapper.createArrayNode();
        for (int index = 0; index < 100; index++) {
            ObjectNode contributor = duplicated.addObject();
            contributor.put("contributions", 8);
            contributor.putObject("author")
                    .put("login", "octocat")
                    .put("avatar_url", "https://avatars.githubusercontent.com/u/1")
                    .put("html_url", "https://github.com/octocat");
        }
        github.contributors = duplicated;

        var contributors = service.analyzeContributors(
                REPOSITORY_URL,
                "test-token"
        );

        assertEquals(1, contributors.totalReturned());
        assertEquals(1, contributors.contributors().size());
        assertEquals(8, contributors.contributors().getFirst().contributions());
        assertTrue(contributors.mayBeTruncated());
        assertNull(contributors.contributors().getFirst().contributionShare());
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
        private int commitRequests;
        private int commitPage;
        private int commitLimit;
        private String commitAuthor;
        private String commitSince;
        private String commitUntil;

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
            commitRequests++;
            return commits;
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
            commitRequests++;
            commitPage = page;
            commitLimit = limit;
            commitAuthor = author;
            commitSince = since;
            commitUntil = until;
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
