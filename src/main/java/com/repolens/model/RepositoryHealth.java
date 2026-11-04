package com.repolens.model;

import java.util.List;

public record RepositoryHealth(
        String repositoryFullName,
        Integer score,
        int availableWeight,
        List<HealthScoreFactor> factors,
        List<String> strengths,
        List<String> areasForAttention,
        String disclaimer
) {}
