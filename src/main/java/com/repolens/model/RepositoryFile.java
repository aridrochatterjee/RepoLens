package com.repolens.model;

public record RepositoryFile(
        String path,
        String name,
        long sizeBytes,
        String language,
        String content,
        String encoding,
        boolean binary
) {}
