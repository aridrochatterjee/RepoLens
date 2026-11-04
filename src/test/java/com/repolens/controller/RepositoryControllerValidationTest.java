package com.repolens.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.repolens.client.GitHubApiException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RepositoryControllerValidationTest {

    private final RepositoryController controller =
            new RepositoryController(null);

    @Test
    void acceptsBoundedCommitPagination() {
        var pagination = controller.parsePagination(
                Map.of("page", "100", "limit", "30")
        );

        assertEquals(100, pagination.page());
        assertEquals(30, pagination.limit());
    }

    @Test
    void rejectsOutOfRangePagination() {
        assertThrows(
                IllegalArgumentException.class,
                () -> controller.parsePagination(
                        Map.of("page", "101", "limit", "10")
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> controller.parsePagination(
                        Map.of("page", "1", "limit", "31")
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> controller.parsePagination(
                        Map.of("page", "zero", "limit", "10")
                )
        );
    }

    @Test
    void mapsGitHubRateLimitsAndProcessingResponsesSafely() {
        assertEquals(
                403,
                controller.mapGitHubStatus(
                        new GitHubApiException("Rate limited.", 403)
                )
        );
        assertEquals(
                429,
                controller.mapGitHubStatus(
                        new GitHubApiException("Rate limited.", 429)
                )
        );
        assertEquals(
                503,
                controller.mapGitHubStatus(
                        new GitHubApiException("Calculating.", 202)
                )
        );
    }
}
