package com.repolens.model;

public record LanguageStat(
        String language,
        long bytes,
        double percentage
) {}