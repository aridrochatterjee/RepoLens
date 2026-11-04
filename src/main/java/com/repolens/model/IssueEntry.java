package com.repolens.model;

import java.util.List;

public record IssueEntry(
        long number,
        String title,
        String state,
        String author,
        String authorAvatarUrl,
        String htmlUrl,
        String createdAt,
        String updatedAt,
        String closedAt,
        int comments,
        List<String> labels
) {}
