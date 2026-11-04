package com.repolens.model;

import java.util.List;

public record LanguageAnalytics(
        String repositoryFullName,
        long totalBytes,
        List<LanguageStat> languages
) {}