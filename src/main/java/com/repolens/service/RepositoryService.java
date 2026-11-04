
package com.repolens.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.repolens.client.GitHubApiClient;
import com.repolens.model.LanguageAnalytics;
import com.repolens.model.LanguageStat;
import com.repolens.model.Repository;
import com.repolens.util.RepositoryUrlParser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RepositoryService {

    private final GitHubApiClient githubApiClient;
    private final ObjectMapper objectMapper;

    public RepositoryService(GitHubApiClient githubApiClient) {
        this.githubApiClient = githubApiClient;
        this.objectMapper = new ObjectMapper();
    }

    public Repository analyzeRepository(
            String repositoryUrl,
            String accessToken
    ) {
        var parsed = RepositoryUrlParser.parse(repositoryUrl);

        JsonNode response = githubApiClient.getRepository(
                parsed.owner(),
                parsed.repository(),
                accessToken
        );

        try {
            return objectMapper.treeToValue(
                    response,
                    Repository.class
            );

        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to process GitHub repository response.",
                    exception
            );
        }
    }

    public LanguageAnalytics analyzeLanguages(
            String repositoryUrl,
            String accessToken
    ) {
        var parsed = RepositoryUrlParser.parse(repositoryUrl);

        JsonNode response = githubApiClient.getRepositoryLanguages(
                parsed.owner(),
                parsed.repository(),
                accessToken
        );

        List<LanguageStat> languages = new ArrayList<>();
        long totalBytes = 0;

        var fields = response.fields();

        while (fields.hasNext()) {
            var entry = fields.next();

            if (!entry.getValue().isNumber()) {
                continue;
            }

            long bytes = entry.getValue().longValue();

            if (bytes < 0) {
                continue;
            }

            totalBytes += bytes;

            languages.add(new LanguageStat(
                    entry.getKey(),
                    bytes,
                    0.0
            ));
        }

        final long calculatedTotal = totalBytes;

        List<LanguageStat> calculatedLanguages = languages.stream()
                .map(language -> {
                    double percentage = calculatedTotal == 0
                            ? 0.0
                            : BigDecimal.valueOf(language.bytes())
                                    .multiply(BigDecimal.valueOf(100))
                                    .divide(
                                            BigDecimal.valueOf(calculatedTotal),
                                            2,
                                            RoundingMode.HALF_UP
                                    )
                                    .doubleValue();

                    return new LanguageStat(
                            language.language(),
                            language.bytes(),
                            percentage
                    );
                })
                .sorted(
                        Comparator.comparingLong(LanguageStat::bytes)
                                .reversed()
                )
                .toList();

        return new LanguageAnalytics(
                parsed.owner() + "/" + parsed.repository(),
                totalBytes,
                calculatedLanguages
        );
    }
}
