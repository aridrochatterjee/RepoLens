package com.repolens.model;

public record RepositoryTreeEntry(
        String path,
        String name,
        String type,
        String extension,
        Long sizeBytes,
        String htmlUrl
) {}
