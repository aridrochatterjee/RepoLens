package com.repolens.model;

public record LargestFile(
        String name,
        String path,
        String extension,
        String directory,
        Long sizeBytes,
        String htmlUrl
) {}
