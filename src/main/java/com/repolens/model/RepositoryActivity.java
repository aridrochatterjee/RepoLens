package com.repolens.model;

import java.util.List;

public record RepositoryActivity(
        String repositoryFullName,
        int periodDays,
        Integer totalCommits,
        Double averageCommitsPerWeek,
        String mostActiveDay,
        List<CommitActivityDay> dailyActivity,
        List<CommitActivityWeek> weeklyActivity,
        boolean available,
        String unavailableReason
) {}
