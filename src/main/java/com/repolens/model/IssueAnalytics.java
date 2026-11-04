package com.repolens.model;

import java.util.List;

public record IssueAnalytics(
        String repositoryFullName,
        int periodDays,
        int page,
        int limit,
        boolean hasNextPage,
        int totalReturned,
        int openIssues,
        int closedIssues,
        Double averageResolutionHours,
        List<ActivityPoint> activity,
        List<IssueEntry> recentlyOpened,
        List<IssueEntry> recentlyClosed,
        List<IssueEntry> recentlyActive,
        List<LabelCount> labels,
        List<AuthorShare> authorDistribution,
        String scope,
        String limitation
) {
    public record LabelCount(String name, int count) {}

    public record AuthorShare(String username, int issueCount, double percentage) {}
}
