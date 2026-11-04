package com.repolens.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.repolens.client.GitHubApiClient;
import com.repolens.client.GitHubApiException;
import com.repolens.model.ActivityPoint;
import com.repolens.model.CollaborationInsights;
import com.repolens.model.CommitActivityDay;
import com.repolens.model.CommitEntry;
import com.repolens.model.ContributorProfile;
import com.repolens.model.ContributorStat;
import com.repolens.model.ContributorStats;
import com.repolens.model.IssueAnalytics;
import com.repolens.model.IssueEntry;
import com.repolens.model.PullRequestAnalytics;
import com.repolens.model.PullRequestEntry;
import com.repolens.util.RepositoryUrlParser;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class DeveloperIntelligenceService {

    private static final int MAX_REVIEW_REQUESTS = 5;
    private final GitHubApiClient githubApiClient;
    private final RepositoryService repositoryService;

    public DeveloperIntelligenceService(
            GitHubApiClient githubApiClient,
            RepositoryService repositoryService
    ) {
        this.githubApiClient = githubApiClient;
        this.repositoryService = repositoryService;
    }

    public ContributorProfile analyzeContributor(
            String repositoryUrl,
            String username,
            String accessToken,
            int limit
    ) {
        if (username == null
                || !username.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?")) {
            throw new IllegalArgumentException("Invalid GitHub username.");
        }
        var repository = RepositoryUrlParser.parse(repositoryUrl);
        String fullName = repository.owner() + "/" + repository.repository();
        JsonNode profile = null;
        try {
            profile = githubApiClient.getUser(username, accessToken);
        } catch (GitHubApiException exception) {
            if (exception.getStatusCode() != 404) throw exception;
        }

        JsonNode commitsResponse = githubApiClient.getRepositoryCommitsByAuthor(
                repository.owner(),
                repository.repository(),
                username,
                1,
                limit,
                accessToken
        );
        if (commitsResponse == null || !commitsResponse.isArray()) {
            throw invalidResponse("contributor commit list");
        }

        List<CommitEntry> commits = new ArrayList<>();
        List<Instant> observedDates = new ArrayList<>();
        Map<LocalDate, Integer> activity = new TreeMap<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int day = 29; day >= 0; day--) {
            activity.put(today.minusDays(day), 0);
        }
        for (JsonNode item : commitsResponse) {
            String sha = text(item.path("sha"));
            if (sha == null || sha.isBlank()) continue;
            JsonNode commit = item.path("commit");
            JsonNode author = item.path("author");
            JsonNode details = commit.path("author");
            String date = text(details.path("date"));
            Instant instant = parseInstant(date);
            if (instant != null) {
                observedDates.add(instant);
                LocalDate day = instant.atZone(ZoneOffset.UTC).toLocalDate();
                activity.computeIfPresent(day, (ignored, count) -> count + 1);
            }
            String authorName = text(author.path("login"));
            if (authorName == null) authorName = text(details.path("name"));
            String message = text(commit.path("message"));
            commits.add(new CommitEntry(
                    sha,
                    sha.substring(0, Math.min(7, sha.length())),
                    message == null ? "Commit message unavailable" : message,
                    authorName == null ? username : authorName,
                    text(author.path("avatar_url")),
                    date,
                    text(item.path("html_url"))
            ));
        }
        observedDates.sort(Comparator.naturalOrder());

        Integer authoredPullRequests = searchCount(
                repository.owner(),
                repository.repository(),
                "is:pr author:" + username,
                accessToken
        );
        Integer authoredIssues = searchCount(
                repository.owner(),
                repository.repository(),
                "is:issue author:" + username,
                accessToken
        );
        Integer reviewedPullRequests = searchCount(
                repository.owner(),
                repository.repository(),
                "is:pr reviewed-by:" + username,
                accessToken
        );
        Integer contributions = repositoryService.analyzeContributors(
                        repositoryUrl,
                        accessToken
                ).contributors().stream()
                .filter(contributor -> contributor.login() != null
                        && contributor.login().equalsIgnoreCase(username))
                .map(ContributorStat::contributions)
                .findFirst()
                .orElse(null);

        String displayName = text(profile == null
                ? null : profile.path("name"));
        String avatar = text(profile == null
                ? null : profile.path("avatar_url"));
        String profileUrl = text(profile == null
                ? null : profile.path("html_url"));
        String resolvedLogin = text(profile == null
                ? null : profile.path("login"));
        boolean bot = "Bot".equalsIgnoreCase(text(profile == null
                ? null : profile.path("type")))
                || (resolvedLogin == null ? username : resolvedLogin)
                        .toLowerCase(Locale.ROOT).endsWith("[bot]");
        List<CommitActivityDay> dailyActivity = activity.entrySet().stream()
                .map(entry -> new CommitActivityDay(
                        entry.getKey().toString(),
                        entry.getValue()
                ))
                .toList();
        return new ContributorProfile(
                fullName,
                resolvedLogin == null ? username : resolvedLogin,
                displayName,
                avatar,
                profileUrl,
                bot,
                contributions,
                commits,
                dailyActivity,
                observedDates.isEmpty()
                        ? null : observedDates.getFirst().toString(),
                observedDates.isEmpty()
                        ? null : observedDates.getLast().toString(),
                authoredPullRequests,
                authoredIssues,
                reviewedPullRequests,
                "Commit dates and first/most-recent observations cover only the "
                        + commits.size() + " latest matching commits requested "
                        + "from GitHub, not the contributor's complete history. "
                        + "Authored and reviewed totals come from GitHub search "
                        + "and can be affected by its indexing and result limits. "
                        + "No productivity or personal-characteristic score is computed."
        );
    }

    public PullRequestAnalytics analyzePullRequests(
            String repositoryUrl,
            String accessToken,
            int periodDays,
            int page,
            int limit
    ) {
        var repository = RepositoryUrlParser.parse(repositoryUrl);
        String fullName = repository.owner() + "/" + repository.repository();
        JsonNode response = githubApiClient.getRepositoryPullRequests(
                repository.owner(),
                repository.repository(),
                page,
                limit,
                accessToken
        );
        if (response == null || !response.isArray()) {
            throw invalidResponse("pull request list");
        }
        List<PullRequestEntry> all = new ArrayList<>();
        Map<String, AuthorCount> authors = new HashMap<>();
        Map<LocalDate, int[]> activity = emptyActivity(periodDays);
        int open = 0;
        int closed = 0;
        int merged = 0;
        int drafts = 0;
        long reviewComments = 0;
        List<Double> mergeHours = new ArrayList<>();
        for (JsonNode item : response) {
            PullRequestEntry entry = mapPullRequest(item);
            if (entry == null) continue;
            all.add(entry);
            if ("open".equals(entry.state())) open++;
            if ("closed".equals(entry.state()) || entry.mergedAt() != null) {
                closed++;
            }
            if (entry.mergedAt() != null) merged++;
            if (entry.draft()) drafts++;
            if (entry.reviewComments() != null) {
                reviewComments += Math.max(0, entry.reviewComments());
            }
            if (entry.author() != null) {
                authors.compute(entry.author(), (ignored, current) -> {
                    AuthorCount count = current == null
                            ? new AuthorCount(entry.authorAvatarUrl()) : current;
                    count.count++;
                    return count;
                });
            }
            Instant created = parseInstant(entry.createdAt());
            Instant mergedAt = parseInstant(entry.mergedAt());
            if (created != null && mergedAt != null
                    && !mergedAt.isBefore(created)) {
                mergeHours.add(Duration.between(created, mergedAt)
                        .toMillis() / 3_600_000.0);
            }
            recordActivity(activity, created, 0);
            recordActivity(activity, parseInstant(entry.closedAt()), 1);
        }

        LocalDate periodCutoff = LocalDate.now(ZoneOffset.UTC)
                .minusDays(periodDays - 1L);
        List<PullRequestEntry> recentlyMerged = all.stream()
                .filter(entry -> entry.mergedAt() != null)
                .filter(entry -> inPeriod(entry.mergedAt(), periodCutoff))
                .sorted(Comparator.comparing(
                        (PullRequestEntry entry) -> parseInstant(entry.mergedAt()),
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(10)
                .toList();
        List<PullRequestEntry> recentlyOpened = all.stream()
                .filter(entry -> inPeriod(entry.createdAt(), periodCutoff))
                .sorted(Comparator.comparing(
                        (PullRequestEntry entry) -> parseInstant(entry.createdAt()),
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(10)
                .toList();

        ReviewSummary reviews = reviewSummary(
                repository.owner(),
                repository.repository(),
                all,
                accessToken
        );
        int closedDenominator = closed;
        Double mergeRate = closedDenominator == 0 ? null
                : roundPercent(merged * 100.0 / closedDenominator);
        List<PullRequestAnalytics.ContributorShare> authorDistribution =
                authorShares(authors, all.size());
        return new PullRequestAnalytics(
                fullName,
                periodDays,
                page,
                limit,
                response.size() == limit && page < 10,
                all.size(),
                open,
                closed,
                merged,
                mergeRate,
                mergeHours.isEmpty()
                        ? null
                        : mergeHours.stream().mapToDouble(Double::doubleValue)
                                .average().orElse(0),
                drafts,
                reviewComments,
                reviews.uniqueReviewers(),
                activityPoints(activity),
                recentlyMerged,
                recentlyOpened,
                authorDistribution,
                "Page " + page + " of at most 10, with up to " + limit
                        + " most recently updated "
                        + "pull requests per page; "
                        + "the period applies to the created-date activity chart.",
                "Merge rate is merged / all returned closed pull requests. "
                        + "Average time-to-merge uses only returned PRs with valid "
                        + "created_at and merged_at timestamps. Reviewers are sampled "
                        + "from at most five returned PR review lists. Counts are not "
                        + "repository-wide totals."
        );
    }

    public IssueAnalytics analyzeIssues(
            String repositoryUrl,
            String accessToken,
            int periodDays,
            int page,
            int limit
    ) {
        var repository = RepositoryUrlParser.parse(repositoryUrl);
        String fullName = repository.owner() + "/" + repository.repository();
        JsonNode response = githubApiClient.getRepositoryIssues(
                repository.owner(),
                repository.repository(),
                page,
                limit,
                accessToken
        );
        if (response == null || !response.isArray()) {
            throw invalidResponse("issue list");
        }
        List<IssueEntry> issues = new ArrayList<>();
        Map<LocalDate, int[]> activity = emptyActivity(periodDays);
        Map<String, Integer> labels = new HashMap<>();
        Map<String, Integer> issueAuthors = new HashMap<>();
        int open = 0;
        int closed = 0;
        List<Double> resolutionHours = new ArrayList<>();
        LocalDate cutoff = LocalDate.now(ZoneOffset.UTC)
                .minusDays(periodDays - 1L);
        for (JsonNode item : response) {
            if (item.has("pull_request")) continue;
            IssueEntry issue = mapIssue(item);
            if (issue == null) continue;
            issues.add(issue);
            if (issue.author() != null) {
                issueAuthors.merge(issue.author(), 1, Integer::sum);
            }
            if ("open".equals(issue.state())) open++;
            if ("closed".equals(issue.state())) closed++;
            for (String label : issue.labels()) {
                labels.merge(label, 1, Integer::sum);
            }
            Instant created = parseInstant(issue.createdAt());
            Instant closedAt = parseInstant(issue.closedAt());
            recordActivity(activity, created, 0);
            if (created != null && closedAt != null
                    && !closedAt.isBefore(created)) {
                resolutionHours.add(Duration.between(created, closedAt)
                        .toMillis() / 3_600_000.0);
                recordActivity(activity, closedAt, 1);
            }
        }
        Comparator<IssueEntry> recentCreated = Comparator.comparing(
                (IssueEntry issue) -> parseInstant(issue.createdAt()),
                Comparator.nullsLast(Comparator.reverseOrder())
        );
        Comparator<IssueEntry> recentClosed = Comparator.comparing(
                (IssueEntry issue) -> parseInstant(issue.closedAt()),
                Comparator.nullsLast(Comparator.reverseOrder())
        );
        List<IssueEntry> recentlyOpened = issues.stream()
                .filter(issue -> inPeriod(issue.createdAt(), cutoff))
                .sorted(recentCreated)
                .limit(10)
                .toList();
        List<IssueEntry> recentlyClosed = issues.stream()
                .filter(issue -> issue.closedAt() != null)
                .filter(issue -> inPeriod(issue.closedAt(), cutoff))
                .sorted(recentClosed)
                .limit(10)
                .toList();
        List<IssueEntry> recentlyActive = issues.stream()
                .sorted(Comparator.comparing(
                        (IssueEntry issue) -> parseInstant(issue.updatedAt()),
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(10)
                .toList();
        List<IssueAnalytics.LabelCount> labelCounts = labels.entrySet().stream()
                .map(entry -> new IssueAnalytics.LabelCount(
                        entry.getKey(), entry.getValue()
                ))
                .sorted(Comparator.comparingInt(
                        IssueAnalytics.LabelCount::count
                ).reversed().thenComparing(IssueAnalytics.LabelCount::name))
                .limit(20)
                .toList();
        List<IssueAnalytics.AuthorShare> authorDistribution =
                issueAuthors.entrySet().stream()
                        .map(entry -> new IssueAnalytics.AuthorShare(
                                entry.getKey(),
                                entry.getValue(),
                                roundPercent(entry.getValue() * 100.0
                                        / issues.size())
                        ))
                        .sorted(Comparator.comparingInt(
                                IssueAnalytics.AuthorShare::issueCount
                        ).reversed().thenComparing(
                                IssueAnalytics.AuthorShare::username
                        ))
                        .limit(10)
                        .toList();
        return new IssueAnalytics(
                fullName,
                periodDays,
                page,
                limit,
                response.size() == limit && page < 10,
                issues.size(),
                open,
                closed,
                resolutionHours.isEmpty()
                        ? null
                        : resolutionHours.stream()
                                .mapToDouble(Double::doubleValue)
                                .average().orElse(0),
                activityPoints(activity),
                recentlyOpened,
                recentlyClosed,
                recentlyActive,
                labelCounts,
                authorDistribution,
                "Page " + page + " of at most 10, with up to " + limit
                        + " recently updated issue "
                        + "API entries per page; pull "
                        + "requests are explicitly excluded. Recent lists use the "
                        + "selected " + periodDays + "-day date window.",
                "Counts and resolution time use only returned issue entries. "
                        + "The Issues API includes pull requests; entries with "
                        + "pull_request are removed before any issue calculation. "
                        + "Missing or invalid timestamps are excluded from time metrics."
        );
    }

    public CollaborationInsights analyzeCollaboration(
            String repositoryUrl,
            String accessToken
    ) {
        ContributorStats stats = repositoryService.analyzeContributors(
                repositoryUrl,
                accessToken
        );
        List<ContributorStat> contributors = stats.contributors();
        long total = stats.totalContributionsReturned() == null
                ? 0 : stats.totalContributionsReturned();
        int named = (int) contributors.stream()
                .filter(item -> item.login() != null)
                .count();
        int bots = (int) contributors.stream()
                .filter(ContributorStat::bot)
                .count();
        Double topShare = stats.mayBeTruncated()
                ? null : shareOf(contributors, total, 1);
        Double topFiveShare = stats.mayBeTruncated()
                ? null : shareOf(contributors, total, 5);
        List<CollaborationInsights.Observation> observations =
                new ArrayList<>();
        if (topShare != null && topShare >= 50) {
            observations.add(new CollaborationInsights.Observation(
                    "Contribution concentration in returned data",
                    "The top returned contributor accounts for "
                            + String.format(Locale.ROOT, "%.1f%%", topShare)
                            + " of returned contributions."
            ));
        }
        if (topFiveShare != null) {
            observations.add(new CollaborationInsights.Observation(
                    "Top-five contribution share",
                    "The five highest-ranked returned contributors account for "
                            + String.format(Locale.ROOT, "%.1f%%", topFiveShare)
                            + " of returned contributions."
            ));
        }
        String fullName = contributors.isEmpty()
                ? RepositoryUrlParser.parse(repositoryUrl).owner() + "/"
                        + RepositoryUrlParser.parse(repositoryUrl).repository()
                : stats.repositoryFullName();
        return new CollaborationInsights(
                fullName,
                stats.available(),
                stats.totalReturned() == null ? contributors.size()
                        : stats.totalReturned(),
                stats.totalContributionsReturned() == null
                        ? 0 : stats.totalContributionsReturned(),
                stats.anonymousContributions(),
                topShare,
                topFiveShare,
                named,
                bots,
                contributors,
                observations,
                stats.limitation()
        );
    }

    private Integer searchCount(
            String owner,
            String repository,
            String qualifier,
            String accessToken
    ) {
        try {
            JsonNode response = githubApiClient.searchRepositoryIssues(
                    owner, repository, qualifier, accessToken
            );
            JsonNode count = response == null
                    ? null : response.path("total_count");
            return count != null && count.canConvertToInt()
                    ? count.asInt() : null;
        } catch (GitHubApiException exception) {
            if (exception.getStatusCode() == 404
                    || exception.getStatusCode() == 422) {
                return null;
            }
            throw exception;
        }
    }

    private ReviewSummary reviewSummary(
            String owner,
            String repository,
            List<PullRequestEntry> pullRequests,
            String accessToken
    ) {
        List<PullRequestEntry> reviewTargets = pullRequests.stream()
                .filter(entry -> entry.number() > 0)
                .limit(MAX_REVIEW_REQUESTS)
                .toList();
        Map<String, Boolean> reviewers = new HashMap<>();
        int successfulReviewLists = 0;
        for (PullRequestEntry pullRequest : reviewTargets) {
            JsonNode response;
            try {
                response = githubApiClient.getPullRequestReviews(
                        owner,
                        repository,
                        pullRequest.number(),
                        accessToken
                );
            } catch (GitHubApiException exception) {
                if (exception.getStatusCode() == 404) continue;
                throw exception;
            }
            if (response == null || !response.isArray()) {
                throw invalidResponse("pull request review list");
            }
            successfulReviewLists++;
            for (JsonNode review : response) {
                String login = text(review.path("user").path("login"));
                boolean bot = "Bot".equalsIgnoreCase(text(
                        review.path("user").path("type")
                ));
                if (login != null && !bot && !isBot(login)) {
                    reviewers.put(login, true);
                }
            }
        }
        return new ReviewSummary(successfulReviewLists == 0
                ? null : reviewers.size());
    }

    private PullRequestEntry mapPullRequest(JsonNode item) {
        long number = item.path("number").asLong(-1);
        String state = text(item.path("state"));
        String title = text(item.path("title"));
        if (number < 1 || state == null || title == null) return null;
        JsonNode user = item.path("user");
        JsonNode reviewComments = item.path("review_comments");
        return new PullRequestEntry(
                number,
                title,
                state,
                item.path("draft").asBoolean(false),
                text(user.path("login")),
                text(user.path("avatar_url")),
                text(item.path("html_url")),
                text(item.path("created_at")),
                text(item.path("closed_at")),
                text(item.path("merged_at")),
                reviewComments.isNumber() ? reviewComments.asInt() : null
        );
    }

    private IssueEntry mapIssue(JsonNode item) {
        long number = item.path("number").asLong(-1);
        String title = text(item.path("title"));
        String state = text(item.path("state"));
        if (number < 1 || title == null || state == null) return null;
        JsonNode user = item.path("user");
        List<String> labels = new ArrayList<>();
        JsonNode labelNodes = item.path("labels");
        if (labelNodes.isArray()) {
            for (JsonNode label : labelNodes) {
                String name = text(label.path("name"));
                if (name != null) labels.add(name);
            }
        }
        return new IssueEntry(
                number,
                title,
                state,
                text(user.path("login")),
                text(user.path("avatar_url")),
                text(item.path("html_url")),
                text(item.path("created_at")),
                text(item.path("updated_at")),
                text(item.path("closed_at")),
                Math.max(0, item.path("comments").asInt(0)),
                List.copyOf(labels)
        );
    }

    private Map<LocalDate, int[]> emptyActivity(int periodDays) {
        Map<LocalDate, int[]> result = new TreeMap<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int day = periodDays - 1; day >= 0; day--) {
            result.put(today.minusDays(day), new int[2]);
        }
        return result;
    }

    private void recordActivity(
            Map<LocalDate, int[]> activity,
            Instant instant,
            int index
    ) {
        if (instant == null) return;
        LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();
        int[] counts = activity.get(date);
        if (counts != null) counts[index]++;
    }

    private List<ActivityPoint> activityPoints(
            Map<LocalDate, int[]> activity
    ) {
        return activity.entrySet().stream()
                .map(entry -> new ActivityPoint(
                        entry.getKey().toString(),
                        entry.getValue()[0],
                        entry.getValue()[1]
                ))
                .toList();
    }

    private boolean inPeriod(String value, LocalDate cutoff) {
        Instant instant = parseInstant(value);
        return instant != null
                && !instant.atZone(ZoneOffset.UTC).toLocalDate().isBefore(cutoff);
    }

    private Double shareOf(
            List<ContributorStat> contributors,
            long total,
            int count
    ) {
        if (contributors.isEmpty() || total == 0) return null;
        long partial = contributors.stream()
                .limit(count)
                .mapToLong(ContributorStat::contributions)
                .sum();
        return roundPercent(partial * 100.0 / total);
    }

    private List<PullRequestAnalytics.ContributorShare> authorShares(
            Map<String, AuthorCount> authors,
            int totalReturned
    ) {
        return authors.entrySet().stream()
                .map(entry -> new PullRequestAnalytics.ContributorShare(
                        entry.getKey(),
                        entry.getValue().avatarUrl,
                        entry.getValue().count,
                        totalReturned == 0 ? 0
                                : roundPercent(entry.getValue().count * 100.0
                                        / totalReturned)
                ))
                .sorted(Comparator.comparingInt(
                        PullRequestAnalytics.ContributorShare::count
                ).reversed().thenComparing(
                        PullRequestAnalytics.ContributorShare::username
                ))
                .limit(10)
                .toList();
    }

    private boolean isBot(String username) {
        return username != null
                && username.toLowerCase(Locale.ROOT).endsWith("[bot]");
    }

    private double roundPercent(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String text(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private GitHubApiException invalidResponse(String kind) {
        return new GitHubApiException(
                "GitHub did not return a valid " + kind + ".",
                502
        );
    }

    private static final class AuthorCount {
        private final String avatarUrl;
        private int count;

        private AuthorCount(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }
    }

    private record ReviewSummary(Integer uniqueReviewers) {}
}
