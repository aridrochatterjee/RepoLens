package com.repolens.model;

import java.util.List;

public record CodebaseAnalysis(
        String repositoryFullName,
        String defaultBranch,
        boolean available,
        boolean treeTruncated,
        boolean explorerTruncated,
        String unavailableReason,
        long totalTrackedFiles,
        long totalDirectories,
        long totalKnownBlobSizeBytes,
        long filesMissingSize,
        CodebaseComposition composition,
        List<RepositoryTreeEntry> treeEntries,
        List<DirectoryStat> directories,
        List<LargestFile> largestFiles,
        List<StructuralHotspot> hotspots,
        String limitations
) {}
