package com.repolens.model;

public record FileTypeStat(
        String name,
        long fileCount,
        long knownSizeBytes
) {}
