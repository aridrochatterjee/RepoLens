package com.repolens.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.repolens.client.GitHubApiClient;
import com.repolens.client.GitHubApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeveloperIntelligenceServiceTest {

    private static final String REPOSITORY_URL =
            "https://github.com/octocat/Hello-World";
    private final ObjectMapper mapper = new ObjectMapper();
    private FakeGitHubApiClient github;
    private DeveloperIntelligenceService service;

    @BeforeEach
    void setUp() {
        github = new FakeGitHubApiClient();
        service = new DeveloperIntelligenceService(
                github,
                new RepositoryService(github)
        );
        github.contributors = contributorFixture();
        github.commits = commitFixture();
        github.pullRequests = pullRequestFixture();
        github.issues = issueFixture();
        github.profile = mapper.createObjectNode()
                .put("login", "octocat")
                .put("name", "The Octocat")
                .put("type", "User")
                .put("avatar_url", "https://avatars.githubusercontent.com/u/1")
                .put("html_url", "https://github.com/octocat");
    }

    @Test
    void mapsContributorProfileWithoutInferringMissingProfileFields() {
        github.profile = null;
        var profile = service.analyzeContributor(
                REPOSITORY_URL,
                "octocat",
                "token",
                30
        );

        assertEquals("octocat", profile.username());
        assertNull(profile.displayName());
        assertNull(profile.avatarUrl());
        assertEquals(12, profile.contributions());
        assertEquals(2, profile.recentCommits().size());
        assertEquals(4, profile.authoredPullRequests());
        assertEquals(7, profile.authoredIssues());
        assertEquals(3, profile.reviewedPullRequests());
        assertTrue(profile.firstObservedContribution() != null);
        assertEquals(30, profile.commitActivity().size());
    }

    @Test
    void rejectsMalformedContributorNamesBeforeRequestingGitHub() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.analyzeContributor(
                        REPOSITORY_URL,
                        "bad/name",
                        "token",
                        10
                )
        );
        assertEquals(0, github.commitRequests);
    }

    @Test
    void identifiesBotProfilesFromGitHubAccountType() {
        github.profile = mapper.createObjectNode()
                .put("login", "automation")
                .put("type", "Bot")
                .put("html_url", "https://github.com/automation");

        var profile = service.analyzeContributor(
                REPOSITORY_URL,
                "automation",
                "token",
                10
        );

        assertTrue(profile.bot());
    }

    @Test
    void classifiesMergedClosedDraftAndMergeDurationFromReturnedPullRequests() {
        var analytics = service.analyzePullRequests(
                REPOSITORY_URL,
                "token",
                30,
                1,
                100
        );

        assertEquals(3, analytics.totalReturned());
        assertEquals(1, analytics.openPullRequests());
        assertEquals(2, analytics.closedPullRequests());
        assertEquals(1, analytics.mergedPullRequests());
        assertEquals(50, analytics.mergeRate(), 0.01);
        assertEquals(24.0, analytics.averageTimeToMergeHours(), 0.01);
        assertEquals(1, analytics.draftPullRequests());
        assertEquals(2, analytics.uniqueReviewersObserved());
        assertEquals(30, analytics.activity().size());
    }

    @Test
    void marksMergeRateUnavailableWhenNoClosedPullRequestsWereReturned() {
        ArrayNode pullRequests = mapper.createArrayNode();
        pullRequests.addObject()
                .put("number", 1)
                .put("title", "Still open")
                .put("state", "open")
                .put("created_at", Instant.now().toString());
        github.pullRequests = pullRequests;

        var analytics = service.analyzePullRequests(
                REPOSITORY_URL,
                "token",
                7,
                1,
                100
        );

        assertNull(analytics.mergeRate());
        assertNull(analytics.averageTimeToMergeHours());
    }

    @Test
    void forwardsTheRequestedBoundedPullRequestAndIssuePages() {
        var pullRequests = service.analyzePullRequests(
                REPOSITORY_URL, "token", 90, 2, 17
        );
        var issues = service.analyzeIssues(
                REPOSITORY_URL, "token", 7, 3, 23
        );

        assertEquals(2, pullRequests.page());
        assertEquals(2, github.pullRequestPage);
        assertEquals(17, github.pullRequestLimit);
        assertEquals(3, issues.page());
        assertEquals(3, github.issuePage);
        assertEquals(23, github.issueLimit);
    }

    @Test
    void excludesPullRequestsFromIssueAnalyticsAndUsesTimestampBounds() {
        var analytics = service.analyzeIssues(
                REPOSITORY_URL,
                "token",
                30,
                1,
                100
        );

        assertEquals(2, analytics.totalReturned());
        assertEquals(1, analytics.openIssues());
        assertEquals(1, analytics.closedIssues());
        assertEquals(24.0, analytics.averageResolutionHours(), 0.01);
        assertEquals(2, analytics.recentlyOpened().size());
        assertEquals(1, analytics.recentlyClosed().size());
        assertEquals(2, analytics.recentlyActive().size());
        assertEquals("bug", analytics.labels().getFirst().name());
    }

    @Test
    void reportsCollaborationSharesAndBotCountsFromReturnedContributors() {
        var analytics = service.analyzeCollaboration(REPOSITORY_URL, "token");

        assertEquals(3, analytics.contributorsReturned());
        assertEquals(20, analytics.totalContributionsReturned());
        assertEquals(60.0, analytics.topContributorShare(), 0.01);
        assertEquals(100.0, analytics.topFiveContributorShare(), 0.01);
        assertEquals(3, analytics.namedContributors());
        assertEquals(1, analytics.botContributors());
        assertTrue(analytics.contributors().get(1).bot());
    }

    @Test
    void preservesUnavailableContributorStatisticsInsteadOfReportingZero() {
        github.contributorsFailure = new GitHubApiException(
                "Still calculating.", 202
        );

        var analytics = service.analyzeCollaboration(REPOSITORY_URL, "token");

        assertFalse(analytics.available());
        assertNull(analytics.topContributorShare());
        assertTrue(analytics.contributors().isEmpty());
    }

    @Test
    void propagatesRateLimitErrorsAndRejectsMalformedGitHubPayloads() {
        github.pullRequestFailure = new GitHubApiException("Rate limited.", 403);
        GitHubApiException error = assertThrows(
                GitHubApiException.class,
                () -> service.analyzePullRequests(
                        REPOSITORY_URL, "token", 30, 1, 100
                )
        );
        assertEquals(403, error.getStatusCode());

        github.pullRequestFailure = null;
        github.pullRequests = mapper.createObjectNode();
        assertEquals(502, assertThrows(
                GitHubApiException.class,
                () -> service.analyzePullRequests(
                        REPOSITORY_URL, "token", 30, 1, 100
                )
        ).getStatusCode());
    }

    @Test
    void distinguishesUnavailableSearchCountsFromZero() {
        github.searchFailureQualifier = "is:issue author:octocat";
        github.searchFailure = new GitHubApiException("No results.", 422);

        var profile = service.analyzeContributor(
                REPOSITORY_URL,
                "octocat",
                "token",
                10
        );

        assertEquals(4, profile.authoredPullRequests());
        assertNull(profile.authoredIssues());
    }

    private JsonNode contributorFixture() {
        ArrayNode contributors = mapper.createArrayNode();
        contributor(contributors, "octocat", 12, "User");
        contributor(contributors, "automation[bot]", 6, "Bot");
        contributor(contributors, "other", 2, "User");
        return contributors;
    }

    private void contributor(
            ArrayNode array,
            String username,
            int contributions,
            String type
    ) {
        ObjectNode item = array.addObject();
        item.put("contributions", contributions);
        item.putObject("author")
                .put("login", username)
                .put("type", type)
                .put("avatar_url", "https://avatars.githubusercontent.com/u/1")
                .put("html_url", "https://github.com/" + username);
    }

    private JsonNode commitFixture() {
        ArrayNode commits = mapper.createArrayNode();
        commit(commits, "a".repeat(40), Instant.now().minusSeconds(3600));
        commit(commits, "b".repeat(40), Instant.now().minusSeconds(86400));
        return commits;
    }

    private void commit(ArrayNode array, String sha, Instant date) {
        ObjectNode item = array.addObject();
        item.put("sha", sha);
        item.put("html_url", "https://github.com/octocat/Hello-World/commit/" + sha);
        item.putObject("author")
                .put("login", "octocat")
                .put("avatar_url", "https://avatars.githubusercontent.com/u/1");
        item.putObject("commit")
                .put("message", "Change")
                .putObject("author")
                .put("name", "Octocat")
                .put("date", date.toString());
    }

    private JsonNode pullRequestFixture() {
        Instant now = Instant.now();
        ArrayNode entries = mapper.createArrayNode();
        pullRequest(entries, 1, "open", false, now.minusSeconds(7200), null, null);
        pullRequest(entries, 2, "closed", false, now.minusSeconds(172800),
                now.minusSeconds(86400), now.minusSeconds(86400));
        pullRequest(entries, 3, "closed", true, now.minusSeconds(3600),
                now.minusSeconds(1000), null);
        return entries;
    }

    private void pullRequest(
            ArrayNode array,
            int number,
            String state,
            boolean draft,
            Instant created,
            Instant closed,
            Instant merged
    ) {
        ObjectNode item = array.addObject();
        item.put("number", number);
        item.put("title", "Pull request " + number);
        item.put("state", state);
        item.put("draft", draft);
        item.put("created_at", created.toString());
        if (closed != null) item.put("closed_at", closed.toString());
        if (merged != null) item.put("merged_at", merged.toString());
        item.put("html_url", "https://github.com/octocat/Hello-World/pull/" + number);
        item.put("review_comments", number);
        item.putObject("user")
                .put("login", "octocat")
                .put("avatar_url", "https://avatars.githubusercontent.com/u/1");
    }

    private JsonNode issueFixture() {
        Instant now = Instant.now();
        ArrayNode entries = mapper.createArrayNode();
        issue(entries, 10, "open", now.minusSeconds(3600), null);
        issue(entries, 11, "closed", now.minusSeconds(172800), now.minusSeconds(86400));
        ObjectNode pullRequest = entries.addObject();
        pullRequest.put("number", 12);
        pullRequest.put("title", "This is a pull request");
        pullRequest.put("state", "closed");
        pullRequest.putObject("pull_request");
        return entries;
    }

    private void issue(
            ArrayNode array,
            int number,
            String state,
            Instant created,
            Instant closed
    ) {
        ObjectNode item = array.addObject();
        item.put("number", number);
        item.put("title", "Issue " + number);
        item.put("state", state);
        item.put("created_at", created.toString());
        item.put("updated_at", Instant.now().toString());
        if (closed != null) item.put("closed_at", closed.toString());
        item.put("comments", 2);
        item.put("html_url", "https://github.com/octocat/Hello-World/issues/" + number);
        item.putObject("user").put("login", "octocat");
        item.putArray("labels").addObject().put("name", "bug");
    }

    private final class FakeGitHubApiClient extends GitHubApiClient {
        private JsonNode contributors;
        private JsonNode commits;
        private JsonNode pullRequests;
        private JsonNode issues;
        private JsonNode profile;
        private GitHubApiException pullRequestFailure;
        private GitHubApiException contributorsFailure;
        private GitHubApiException searchFailure;
        private String searchFailureQualifier;
        private int commitRequests;
        private int pullRequestPage;
        private int pullRequestLimit;
        private int issuePage;
        private int issueLimit;
        private final List<Long> reviewRequests = new ArrayList<>();

        @Override
        public JsonNode getRepositoryContributors(
                String owner,
                String repository,
                String token,
                int page,
                int limit
        ) {
            if (contributorsFailure != null) throw contributorsFailure;
            return page == 1 ? contributors : mapper.createArrayNode();
        }

        @Override
        public JsonNode getRepositoryCommitsByAuthor(
                String owner,
                String repository,
                String username,
                int page,
                int limit,
                String token
        ) {
            commitRequests++;
            return commits;
        }

        @Override
        public JsonNode getUser(String username, String token) {
            if (profile == null) {
                throw new GitHubApiException("Not found.", 404);
            }
            return profile;
        }

        @Override
        public JsonNode searchRepositoryIssues(
                String owner,
                String repository,
                String qualifier,
                String token
        ) {
            if (qualifier.equals(searchFailureQualifier)) throw searchFailure;
            int count = qualifier.contains("author:") && qualifier.contains("is:pr")
                    ? 4 : qualifier.contains("reviewed-by:") ? 3 : 7;
            return mapper.createObjectNode().put("total_count", count);
        }

        @Override
        public JsonNode getRepositoryPullRequests(
                String owner,
                String repository,
                int page,
                int limit,
                String token
        ) {
            if (pullRequestFailure != null) throw pullRequestFailure;
            pullRequestPage = page;
            pullRequestLimit = limit;
            return pullRequests;
        }

        @Override
        public JsonNode getRepositoryIssues(
                String owner,
                String repository,
                int page,
                int limit,
                String token
        ) {
            issuePage = page;
            issueLimit = limit;
            return issues;
        }

        @Override
        public JsonNode getPullRequestReviews(
                String owner,
                String repository,
                long number,
                String token
        ) {
            reviewRequests.add(number);
            ArrayNode reviews = mapper.createArrayNode();
            reviews.addObject().putObject("user").put("login", "reviewer");
            reviews.addObject().putObject("user").put("login", "second-reviewer");
            return reviews;
        }
    }
}
