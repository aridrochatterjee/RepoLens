package com.repolens.model;

public record HealthScoreFactor(
        String name,
        Integer score,
        int weight,
        String explanation
) {}
