package com.repolens.model;

import java.util.List;

public record CollaborationInsights(
        String repositoryFullName,
        boolean available,
        int contributorsReturned,
        long totalContributionsReturned,
        Long anonymousContributions,
        Double topContributorShare,
        Double topFiveContributorShare,
        int namedContributors,
        int botContributors,
        List<ContributorStat> contributors,
        List<Observation> observations,
        String limitation
) {
    public record Observation(String title, String evidence) {}
}
