
package com.repolens.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.repolens.client.GitHubApiClient;
import com.repolens.client.GitHubApiException;
import com.repolens.model.LanguageAnalytics;
import com.repolens.model.LanguageStat;
import com.repolens.model.Repository;
import com.repolens.model.CommitActivityDay;
import com.repolens.model.CommitActivityWeek;
import com.repolens.model.CommitEntry;
import com.repolens.model.CommitPage;
import com.repolens.model.ContributorStat;
import com.repolens.model.ContributorStats;
import com.repolens.model.HealthScoreFactor;
import com.repolens.model.RepositoryActivity;
import com.repolens.model.RepositoryHealth;
import com.repolens.util.RepositoryUrlParser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeMap;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

public class RepositoryService {

    private final GitHubApiClient githubApiClient;
    private final ObjectMapper objectMapper;

    public RepositoryService(GitHubApiClient githubApiClient) {
        this.githubApiClient = githubApiClient;
        this.objectMapper = new ObjectMapper();
    }

    public Repository analyzeRepository(
            String repositoryUrl,
            String accessToken
    ) {
        var parsed = RepositoryUrlParser.parse(repositoryUrl);

        JsonNode response = githubApiClient.getRepository(
                parsed.owner(),
                parsed.repository(),
                accessToken
        );
        if (response == null || !response.isObject()) {
            throw new GitHubApiException(
                    "GitHub did not return valid repository metadata.",
                    502
            );
        }

        try {
            return objectMapper.treeToValue(
                    response,
                    Repository.class
            );

        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to process GitHub repository response.",
                    exception
            );
        }
    }

    public LanguageAnalytics analyzeLanguages(
            String repositoryUrl,
            String accessToken
    ) {
        var parsed = RepositoryUrlParser.parse(repositoryUrl);

        JsonNode response = githubApiClient.getRepositoryLanguages(
                parsed.owner(),
                parsed.repository(),
                accessToken
        );
        if (response == null || !response.isObject()) {
            throw new GitHubApiException(
                    "GitHub did not return valid language statistics.",
                    502
            );
        }

        List<LanguageStat> languages = new ArrayList<>();
        long totalBytes = 0;

        for (var entry : response.properties()) {
            if (!entry.getValue().isNumber()) {
                continue;
            }

            long bytes = entry.getValue().longValue();

            if (bytes < 0) {
                continue;
            }

            totalBytes += bytes;

            languages.add(new LanguageStat(
                    entry.getKey(),
                    bytes,
                    0.0
            ));
        }

        final long calculatedTotal = totalBytes;

        List<LanguageStat> calculatedLanguages = languages.stream()
                .map(language -> {
                    double percentage = calculatedTotal == 0
                            ? 0.0
                            : BigDecimal.valueOf(language.bytes())
                                    .multiply(BigDecimal.valueOf(100))
                                    .divide(
                                            BigDecimal.valueOf(calculatedTotal),
                                            2,
                                            RoundingMode.HALF_UP
                                    )
                                    .doubleValue();

                    return new LanguageStat(
                            language.language(),
                            language.bytes(),
                            percentage
                    );
                })
                .sorted(
                        Comparator.comparingLong(LanguageStat::bytes)
                                .reversed()
                )
                .toList();

        return new LanguageAnalytics(
                parsed.owner() + "/" + parsed.repository(),
                totalBytes,
                calculatedLanguages
        );
    }

    public RepositoryActivity analyzeActivity(
            String repositoryUrl,
            String accessToken,
            int periodDays
    ) {
        var parsed = RepositoryUrlParser.parse(repositoryUrl);
        JsonNode response;

        try {
            response = githubApiClient.getRepositoryCommitActivity(
                    parsed.owner(),
                    parsed.repository(),
                    accessToken
            );
        } catch (GitHubApiException exception) {
            if (exception.getStatusCode() == 202) {
                return unavailableActivity(
                        parsed.owner() + "/" + parsed.repository(),
                        periodDays,
                        "GitHub is still calculating repository activity. Try again shortly.",
                        "calculating"
                );
            }
            if (exception.getStatusCode() == 204) {
                return unavailableActivity(
                        parsed.owner() + "/" + parsed.repository(),
                        periodDays,
                        "GitHub returned no activity content. It may have no reportable activity, or GitHub may have no statistics to return.",
                        "absent"
                );
            }
            throw exception;
        }

        if (response == null || !response.isArray()) {
            return unavailableActivity(
                    parsed.owner() + "/" + parsed.repository(),
                    periodDays,
                    "GitHub did not return a valid weekly activity series."
            );
        }
        if (response.isEmpty()) {
            return unavailableActivity(
                    parsed.owner() + "/" + parsed.repository(),
                    periodDays,
                    "GitHub returned an empty activity series. This is not a measured zero-commit period.",
                    "absent"
            );
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate requestedFirstDay = today.minusDays(periodDays - 1L);
        TreeMap<LocalDate, JsonNode> weeks = new TreeMap<>();
        for (JsonNode week : response) {
            JsonNode weekNode = week.path("week");
            JsonNode days = week.path("days");
            if (!weekNode.isNumber() || !days.isArray() || days.size() != 7) {
                return unavailableActivity(
                        parsed.owner() + "/" + parsed.repository(),
                        periodDays,
                        "GitHub returned incomplete weekly commit activity data."
                );
            }

            LocalDate weekStart = Instant.ofEpochSecond(weekNode.asLong())
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate();
            if (weeks.putIfAbsent(weekStart, week) != null) {
                return unavailableActivity(
                        parsed.owner() + "/" + parsed.repository(),
                        periodDays,
                        "GitHub returned duplicate weekly commit activity data."
                );
            }
            for (JsonNode day : days) {
                if (!day.isNumber() || day.asInt() < 0) {
                    return unavailableActivity(
                            parsed.owner() + "/" + parsed.repository(),
                            periodDays,
                            "GitHub returned invalid daily commit activity data."
                    );
                }
            }
        }

        LocalDate firstDay = requestedFirstDay;
        if (periodDays == 364 && weeks.firstKey().isAfter(firstDay)) {
            firstDay = weeks.firstKey();
        }
        Map<LocalDate, Integer> dailyCounts = new TreeMap<>();
        Map<LocalDate, Integer> weeklyCounts = new TreeMap<>();
        Set<LocalDate> coveredDays = new HashSet<>();
        for (var entry : weeks.entrySet()) {
            LocalDate weekStart = entry.getKey();
            JsonNode days = entry.getValue().path("days");
            for (int index = 0; index < days.size(); index++) {
                LocalDate date = weekStart.plusDays(index);
                if (!date.isBefore(firstDay) && !date.isAfter(today)) {
                    coveredDays.add(date);
                    int count = days.get(index).asInt();
                    dailyCounts.put(date, count);
                    LocalDate monday = date.with(DayOfWeek.MONDAY);
                    weeklyCounts.merge(monday, count, Integer::sum);
                }
            }
        }

        if (coveredDays.isEmpty()) {
            return unavailableActivity(
                    parsed.owner() + "/" + parsed.repository(),
                    periodDays,
                    "GitHub returned no activity dates within the selected period."
            );
        }

        boolean complete = firstDay.equals(requestedFirstDay)
                && coveredDays.size() == periodDays;
        String coverageNote = complete
                ? null
                : "GitHub returned activity for " + coveredDays.size() + " of "
                        + periodDays + " selected days. Charts and totals include "
                        + "only returned dates; missing dates are not treated as zero."
                        + (firstDay.isAfter(requestedFirstDay)
                                ? " Its weekly series begins on " + firstDay + "."
                                : "");

        List<CommitActivityDay> dailyActivity = dailyCounts.entrySet()
                .stream()
                .map(entry -> new CommitActivityDay(
                        entry.getKey().toString(),
                        entry.getValue()
                ))
                .toList();
        List<CommitActivityWeek> weeklyActivity = weeklyCounts.entrySet()
                .stream()
                .map(entry -> new CommitActivityWeek(
                        entry.getKey().toString(),
                        entry.getValue()
                ))
                .toList();

        int total = dailyCounts.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        Map<DayOfWeek, Integer> dayOfWeekCounts =
                new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            dayOfWeekCounts.put(day, 0);
        }
        dailyCounts.forEach((date, count) ->
                dayOfWeekCounts.compute(
                        date.getDayOfWeek(),
                        (ignored, current) -> current + count
                )
        );

        int maxDayCount = dayOfWeekCounts.values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        long mostActiveDayCount = dayOfWeekCounts.values().stream()
                .filter(count -> count == maxDayCount)
                .count();
        String mostActiveDay = total == 0
                ? null
                : mostActiveDayCount > 1
                        ? "Multiple days"
                        : dayOfWeekCounts.entrySet()
                                .stream()
                                .filter(entry -> entry.getValue() == maxDayCount)
                                .map(entry -> entry.getKey().toString())
                                .findFirst()
                                .orElse(null);

        return new RepositoryActivity(
                parsed.owner() + "/" + parsed.repository(),
                periodDays,
                total,
                Math.round(total * 7.0 / coveredDays.size() * 100.0) / 100.0,
                mostActiveDay,
                dailyActivity,
                weeklyActivity,
                true,
                null,
                complete ? "available" : "incomplete",
                coverageNote
        );
    }

    public CommitPage analyzeCommits(
            String repositoryUrl,
            String accessToken,
            int page,
            int limit
    ) {
        return analyzeCommits(
                repositoryUrl, accessToken, page, limit, null, null, null
        );
    }

    public CommitPage analyzeCommits(
            String repositoryUrl,
            String accessToken,
            int page,
            int limit,
            String authorLogin,
            String since,
            String until
    ) {
        var parsed = RepositoryUrlParser.parse(repositoryUrl);
        if (authorLogin != null && !authorLogin.isBlank()
                && !authorLogin.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?")) {
            throw new IllegalArgumentException("Invalid GitHub username.");
        }
        LocalDate sinceDate = parseCommitDate(since, "since");
        LocalDate untilDate = parseCommitDate(until, "until");
        if (sinceDate != null && untilDate != null
                && sinceDate.isAfter(untilDate)) {
            throw new IllegalArgumentException(
                    "The start date must not be after the end date."
            );
        }
        String sinceTimestamp = sinceDate == null
                ? null : sinceDate.atStartOfDay(ZoneOffset.UTC).toInstant().toString();
        String untilTimestamp = untilDate == null
                ? null : untilDate.plusDays(1).atStartOfDay(ZoneOffset.UTC)
                        .minusNanos(1).toInstant().toString();
        JsonNode response = githubApiClient.getRepositoryCommits(
                parsed.owner(),
                parsed.repository(),
                accessToken,
                page,
                limit,
                authorLogin,
                sinceTimestamp,
                untilTimestamp
        );

        if (response == null || !response.isArray()) {
            throw new GitHubApiException(
                    "GitHub did not return a valid commit list.",
                    502
            );
        }

        List<CommitEntry> commits = new ArrayList<>();
        for (JsonNode item : response) {
            JsonNode commit = item.path("commit");
            JsonNode author = item.path("author");
            JsonNode commitAuthor = commit.path("author");
            String sha = textOrNull(item.path("sha"));
            if (sha == null || sha.isBlank()) {
                continue;
            }

            String authorName = textOrNull(author.path("login"));
            if (authorName == null || authorName.isBlank()) {
                authorName = textOrNull(commitAuthor.path("name"));
            }
            String date = textOrNull(commitAuthor.path("date"));
            if (date == null) {
                date = textOrNull(commit.path("committer").path("date"));
            }
            String message = textOrNull(commit.path("message"));

            commits.add(new CommitEntry(
                    sha,
                    sha.substring(0, Math.min(7, sha.length())),
                    message == null ? "Commit message unavailable" : message,
                    authorName == null || authorName.isBlank()
                            ? "Unknown author"
                            : authorName,
                    textOrNull(author.path("avatar_url")),
                    date,
                    textOrNull(item.path("html_url"))
            ));
        }

        return new CommitPage(
                parsed.owner() + "/" + parsed.repository(),
                page,
                limit,
                commits
        );
    }

    private LocalDate parseCommitDate(String date, String parameter) {
        if (date == null || date.isBlank()) return null;
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    parameter + " must be an ISO date (YYYY-MM-DD)."
            );
        }
    }

    public ContributorStats analyzeContributors(
            String repositoryUrl,
            String accessToken
    ) {
        var parsed = RepositoryUrlParser.parse(repositoryUrl);
        final int pageSize = 100;
        final int maxPages = 5;
        Map<String, JsonNode> returnedByIdentity = new LinkedHashMap<>();
        boolean hasMore = false;

        for (int page = 1; page <= maxPages; page++) {
            JsonNode response;
            try {
                response = githubApiClient.getRepositoryContributors(
                        parsed.owner(),
                        parsed.repository(),
                        accessToken,
                        page,
                        pageSize
                );
            } catch (GitHubApiException exception) {
                if (exception.getStatusCode() == 202) {
                    return new ContributorStats(
                            parsed.owner() + "/" + parsed.repository(),
                            null,
                            null,
                            null,
                            false,
                            false,
                            List.of(),
                            "GitHub is still calculating contributor statistics. Try again shortly.",
                            "calculating"
                    );
                }
                if (exception.getStatusCode() == 204) {
                    return new ContributorStats(
                            parsed.owner() + "/" + parsed.repository(),
                            null,
                            null,
                            null,
                            false,
                            false,
                            List.of(),
                            "GitHub returned no contributor statistics (204 No Content). The repository may have no contributor activity that GitHub can report.",
                            "absent"
                    );
                }
                throw exception;
            }
            if (response == null || !response.isArray()) {
                throw new GitHubApiException(
                        "GitHub did not return a valid contributor list.",
                        502
                );
            }
            int anonymousIndex = 0;
            for (JsonNode contributor : response) {
                JsonNode author = contributor.path("author");
                String login = textOrNull(author.path("login"));
                if (login != null && login.isBlank()) {
                    login = null;
                }
                String identity = login == null
                        ? author.path("id").isNumber()
                                ? "id:" + author.path("id").asLong()
                                : "anonymous:" + page + ":" + anonymousIndex++
                        : "login:" + login.toLowerCase(java.util.Locale.ROOT);
                returnedByIdentity.putIfAbsent(identity, contributor);
            }
            if (response.size() < pageSize) {
                break;
            }
            if (page == maxPages) {
                hasMore = true;
            }
        }

        List<JsonNode> returnedContributors =
                new ArrayList<>(returnedByIdentity.values());
        long totalContributions = returnedContributors.stream()
                .mapToLong(item -> nonNegativeInt(item.path("contributions")))
                .sum();
        long anonymousContributions = returnedContributors.stream()
                .filter(item -> {
                    String login = textOrNull(item.path("author").path("login"));
                    return login == null || login.isBlank();
                })
                .mapToLong(item -> nonNegativeInt(item.path("contributions")))
                .sum();
        final boolean contributorResultsTruncated = hasMore;
        List<ContributorStat> contributors = returnedContributors.stream()
                .map(item -> {
                    JsonNode author = item.path("author");
                    String login = textOrNull(author.path("login"));
                    if (login != null && login.isBlank()) {
                        login = null;
                    }
                    int contributions = nonNegativeInt(
                            item.path("contributions")
                    );
                    Double share = contributorResultsTruncated
                            || totalContributions == 0
                            ? null
                            : Math.round(
                                    contributions * 10000.0
                                            / totalContributions
                            ) / 100.0;
                    return new ContributorStat(
                            login,
                            textOrNull(author.path("avatar_url")),
                            textOrNull(author.path("html_url")),
                            contributions,
                            share,
                            "Bot".equalsIgnoreCase(textOrNull(
                                    author.path("type")
                            )) || textOrNull(author.path("login")) != null
                                    && textOrNull(author.path("login"))
                                            .toLowerCase(java.util.Locale.ROOT)
                                            .endsWith("[bot]")
                    );
                })
                .filter(contributor -> contributor.login() != null)
                .sorted(Comparator.comparingInt(
                                ContributorStat::contributions
                        ).reversed()
                        .thenComparing(contributor ->
                                contributor.login() == null
                                        ? "" : contributor.login().toLowerCase(
                                                java.util.Locale.ROOT
                                        )
                        ))
                .toList();

        String limitation;
        if (hasMore) {
            limitation = "Showing the first 500 contributors returned by GitHub; "
                    + "additional contributors may be omitted.";
        } else if (contributors.isEmpty() && anonymousContributions > 0) {
            limitation = "GitHub returned " + returnedContributors.size()
                    + " contributor record(s), but none contained an available "
                    + "GitHub username. Anonymous contribution counts are retained "
                    + "and are not attributed to an inferred account.";
        } else {
            limitation = "GitHub cannot map every contribution to an account, and "
                    + "private repository activity is not included.";
        }

        return new ContributorStats(
                parsed.owner() + "/" + parsed.repository(),
                returnedContributors.size(),
                totalContributions,
                anonymousContributions,
                true,
                hasMore,
                contributors,
                limitation,
                "available"
        );
    }

    public RepositoryHealth analyzeHealth(
            String repositoryUrl,
            String accessToken
    ) {
        Repository repository = analyzeRepository(repositoryUrl, accessToken);
        RepositoryActivity activity = analyzeActivity(
                repositoryUrl,
                accessToken,
                30
        );
        ContributorStats contributors = analyzeContributors(
                repositoryUrl,
                accessToken
        );

        boolean completeActivity = activity.available()
                && "available".equals(activity.availabilityState());
        Integer activityScore = completeActivity
                ? Math.min(100, activity.totalCommits() * 5)
                : null;
        String activityExplanation = completeActivity
                ? activity.totalCommits() + " commits in the last 30 days; 20 commits reaches the factor's 100-point cap."
                : activity.unavailableReason() != null
                        ? activity.unavailableReason()
                        : activity.coverageNote();

        Integer maintenanceScore = maintenanceScore(repository.pushedAt());
        String maintenanceExplanation = maintenanceScore == null
                ? "No usable last-push timestamp was provided by GitHub."
                : "Last push: " + repository.pushedAt()
                        + "; newer activity scores higher, with 100 points for activity within 30 days.";

        Integer issueScore = completeActivity
                && repository.openIssues() != null
                ? issueScore(repository.openIssues(), activity.totalCommits())
                : null;
        String issueExplanation = issueScore == null
                ? repository.openIssues() == null
                        ? "GitHub did not provide an open-issues count."
                        : "Cannot compare open issues with recent commits while activity data is unavailable or incomplete."
                : repository.openIssues() + " open issues relative to "
                        + activity.totalCommits()
                        + " recent commits; GitHub's open-issues count may include pull requests.";

        Integer contributorScore = contributors.available()
                && !contributors.contributors().isEmpty()
                ? Math.min(100, contributors.contributors().size() * 20)
                : null;
        String contributorExplanation = contributorScore != null
                ? contributors.contributors().size()
                        + " named contributors returned; five or more reaches the factor's 100-point cap."
                : contributors.available()
                        ? "GitHub returned contribution totals without account identities; contributor diversity cannot be scored."
                        : "Contributor activity data is unavailable.";

        long activeWeeks = completeActivity
                ? activity.weeklyActivity().stream()
                        .filter(week -> week.commits() > 0)
                        .count()
                : 0;
        Integer consistencyScore = completeActivity
                ? (int) Math.round(activeWeeks * 100.0
                        / activity.weeklyActivity().size())
                : null;
        String consistencyExplanation = completeActivity
                ? activeWeeks + " of " + activity.weeklyActivity().size()
                        + " calendar weeks had commits in the selected window."
                : "Recent development consistency cannot be measured because activity data is unavailable or incomplete.";

        List<HealthScoreFactor> factors = List.of(
                new HealthScoreFactor(
                        "Recent commit activity",
                        activityScore,
                        20,
                        activityExplanation
                ),
                new HealthScoreFactor(
                        "Maintenance recency",
                        maintenanceScore,
                        20,
                        maintenanceExplanation
                ),
                new HealthScoreFactor(
                        "Open issues relative to activity",
                        issueScore,
                        20,
                        issueExplanation
                ),
                new HealthScoreFactor(
                        "Contributor activity",
                        contributorScore,
                        20,
                        contributorExplanation
                ),
                new HealthScoreFactor(
                        "Development consistency",
                        consistencyScore,
                        20,
                        consistencyExplanation
                )
        );

        int availableWeight = factors.stream()
                .filter(factor -> factor.score() != null)
                .mapToInt(HealthScoreFactor::weight)
                .sum();
        Integer score = availableWeight == 0
                ? null
                : (int) Math.round(factors.stream()
                        .filter(factor -> factor.score() != null)
                        .mapToInt(factor -> factor.score() * factor.weight())
                        .sum() / (double) availableWeight);

        List<String> strengths = factors.stream()
                .filter(factor -> factor.score() != null
                        && factor.score() >= 70)
                .map(HealthScoreFactor::name)
                .toList();
        List<String> attention = factors.stream()
                .filter(factor -> factor.score() != null
                        && factor.score() < 50)
                .map(HealthScoreFactor::name)
                .toList();

        return new RepositoryHealth(
                repository.fullName(),
                score,
                availableWeight,
                factors,
                strengths,
                attention,
                "RepoLens estimate, not an official GitHub rating. Factors are equally weighted and omitted when source data is unavailable. GitHub statistics may be delayed or incomplete."
        );
    }

    private RepositoryActivity unavailableActivity(
            String repositoryFullName,
            int periodDays,
            String reason
    ) {
        return unavailableActivity(
                repositoryFullName,
                periodDays,
                reason,
                "incomplete"
        );
    }

    private RepositoryActivity unavailableActivity(
            String repositoryFullName,
            int periodDays,
            String reason,
            String availabilityState
    ) {
        return new RepositoryActivity(
                repositoryFullName,
                periodDays,
                null,
                null,
                null,
                List.of(),
                List.of(),
                false,
                reason,
                availabilityState,
                null
        );
    }

    private Integer maintenanceScore(String pushedAt) {
        if (pushedAt == null || pushedAt.isBlank()) {
            return null;
        }
        try {
            long daysSincePush = java.time.temporal.ChronoUnit.DAYS.between(
                    Instant.parse(pushedAt)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate(),
                    LocalDate.now(ZoneOffset.UTC)
            );
            if (daysSincePush <= 30) return 100;
            if (daysSincePush <= 90) return 75;
            if (daysSincePush <= 180) return 50;
            if (daysSincePush <= 365) return 25;
            return 0;
        } catch (java.time.DateTimeException exception) {
            return null;
        }
    }

    private int issueScore(int openIssues, int recentCommits) {
        double ratio = Math.max(0, openIssues) / (double) (recentCommits + 1);
        if (ratio <= 0.1) return 100;
        if (ratio <= 0.25) return 80;
        if (ratio <= 0.5) return 60;
        if (ratio <= 1) return 40;
        if (ratio <= 2) return 20;
        return 0;
    }

    private int nonNegativeInt(JsonNode value) {
        return value.isNumber() ? Math.max(0, value.asInt()) : 0;
    }

    private String textOrNull(JsonNode value) {
        return value.isTextual() ? value.asText() : null;
    }
}
