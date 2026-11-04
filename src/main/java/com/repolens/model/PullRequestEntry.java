package com.repolens.model;

public record PullRequestEntry(
        long number,
        String title,
        String state,
        boolean draft,
        String author,
        String authorAvatarUrl,
        String htmlUrl,
        String createdAt,
        String closedAt,
        String mergedAt,
        Integer reviewComments
) {}
