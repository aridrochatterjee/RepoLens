package com.repolens.model;

public record DirectoryStat(
        String path,
        long fileCount,
        long knownSizeBytes,
        double fileSharePercent,
        Double sizeSharePercent
) {}
