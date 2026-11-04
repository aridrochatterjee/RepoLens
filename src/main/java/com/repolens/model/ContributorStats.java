package com.repolens.model;

import java.util.List;

public record ContributorStats(
        String repositoryFullName,
        Integer totalReturned,
        boolean available,
        boolean mayBeTruncated,
        List<ContributorStat> contributors,
        String limitation
) {}
