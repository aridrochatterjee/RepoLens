package com.repolens.model;

public record ContributorStat(
        String login,
        String avatarUrl,
        String profileUrl,
        int contributions,
        double contributionShare
) {}
