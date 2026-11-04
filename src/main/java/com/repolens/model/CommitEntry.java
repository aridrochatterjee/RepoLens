package com.repolens.model;

public record CommitEntry(
        String sha,
        String shortSha,
        String message,
        String author,
        String authorAvatarUrl,
        String date,
        String htmlUrl
) {}
