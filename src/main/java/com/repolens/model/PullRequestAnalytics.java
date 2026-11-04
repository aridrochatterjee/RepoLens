package com.repolens.model;

import java.util.List;

public record PullRequestAnalytics(
        String repositoryFullName,
        int periodDays,
        int page,
        int limit,
        boolean hasNextPage,
        int totalReturned,
        int openPullRequests,
        int closedPullRequests,
        int mergedPullRequests,
        Double mergeRate,
        Double averageTimeToMergeHours,
        int draftPullRequests,
        long reviewComments,
        Integer uniqueReviewersObserved,
        List<ActivityPoint> activity,
        List<PullRequestEntry> recentlyMerged,
        List<PullRequestEntry> recentlyOpened,
        List<ContributorShare> authorDistribution,
        String scope,
        String limitation
) {
    public record ContributorShare(
            String username,
            String avatarUrl,
            int count,
            double percentage
    ) {}
}
