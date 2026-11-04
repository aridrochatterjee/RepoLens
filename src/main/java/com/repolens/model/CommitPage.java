package com.repolens.model;

import java.util.List;

public record CommitPage(
        String repositoryFullName,
        int page,
        int limit,
        List<CommitEntry> commits
) {}
