package com.repolens.model;

import java.util.List;

public record ContributorProfile(
        String repositoryFullName,
        String username,
        String displayName,
        String avatarUrl,
        String profileUrl,
        boolean bot,
        Integer contributions,
        List<CommitEntry> recentCommits,
        List<CommitActivityDay> commitActivity,
        String firstObservedContribution,
        String mostRecentObservedContribution,
        Integer authoredPullRequests,
        Integer authoredIssues,
        Integer reviewedPullRequests,
        String limitation
) {}
