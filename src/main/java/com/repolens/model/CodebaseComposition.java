package com.repolens.model;

import java.util.List;

public record CodebaseComposition(
        long totalTrackedFiles,
        long filesWithKnownSize,
        long filesMissingSize,
        long totalKnownBlobSizeBytes,
        List<FileTypeStat> extensions,
        List<FileTypeStat> languages,
        List<FileTypeStat> categories
) {}
