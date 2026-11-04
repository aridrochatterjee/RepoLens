package com.repolens.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GitHubApiClientTest {

    @Test
    void requestsAnonymousContributorRecordsAlongsideNamedAccounts() {
        var endpoint = GitHubApiClient.repositoryContributorsEndpoint(
                "octocat",
                "Hello-World",
                2,
                100
        );

        assertEquals(
                "https://api.github.com/repos/octocat/Hello-World/contributors",
                endpoint.getScheme() + "://" + endpoint.getAuthority()
                        + endpoint.getPath()
        );
        assertEquals("anon=true&per_page=100&page=2", endpoint.getQuery());
    }
}
