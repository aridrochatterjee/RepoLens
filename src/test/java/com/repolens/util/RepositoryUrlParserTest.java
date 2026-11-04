package com.repolens.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class RepositoryUrlParserTest {

    @Test
    void shouldParseValidRepositoryUrl() {

        var result = RepositoryUrlParser.parse(
                "https://github.com/octocat/Hello-World"
        );

        assertEquals("octocat", result.owner());
        assertEquals("Hello-World", result.repository());

        assertEquals(
                "https://github.com/octocat/Hello-World",
                result.url()
        );
    }

    @Test
    void shouldAcceptTrailingSlash() {

        var result = RepositoryUrlParser.parse(
                "https://github.com/octocat/Hello-World/"
        );

        assertEquals("octocat", result.owner());
        assertEquals("Hello-World", result.repository());
    }

    @Test
    void shouldAcceptGitSuffix() {

        var result = RepositoryUrlParser.parse(
                "https://github.com/octocat/Hello-World.git"
        );

        assertEquals("octocat", result.owner());
        assertEquals("Hello-World", result.repository());
    }

    @Test
    void shouldTrimWhitespace() {

        var result = RepositoryUrlParser.parse(
                "  https://github.com/octocat/Hello-World  "
        );

        assertEquals("octocat", result.owner());
    }

    @Test
    void shouldRejectNullUrl() {

        assertThrows(
                IllegalArgumentException.class,
                () -> RepositoryUrlParser.parse(null)
        );
    }

    @Test
    void shouldRejectEmptyUrl() {

        assertThrows(
                IllegalArgumentException.class,
                () -> RepositoryUrlParser.parse(" ")
        );
    }

    @Test
    void shouldRejectHttpUrl() {

        assertThrows(
                IllegalArgumentException.class,
                () -> RepositoryUrlParser.parse(
                        "http://github.com/octocat/Hello-World"
                )
        );
    }

    @Test
    void shouldRejectNonGitHubDomain() {

        assertThrows(
                IllegalArgumentException.class,
                () -> RepositoryUrlParser.parse(
                        "https://gitlab.com/octocat/Hello-World"
                )
        );
    }

    @Test
    void shouldRejectInvalidRepositoryPath() {

        assertThrows(
                IllegalArgumentException.class,
                () -> RepositoryUrlParser.parse(
                        "https://github.com/octocat"
                )
        );
    }

    @Test
    void shouldRejectExtraPathSegments() {

        assertThrows(
                IllegalArgumentException.class,
                () -> RepositoryUrlParser.parse(
                        "https://github.com/octocat/Hello-World/tree/main"
                )
        );
    }

    @Test
    void shouldRejectQueryParameters() {

        assertThrows(
                IllegalArgumentException.class,
                () -> RepositoryUrlParser.parse(
                        "https://github.com/octocat/Hello-World?tab=readme"
                )
        );
    }

    @Test
    void shouldRejectInvalidOwner() {

        assertThrows(
                IllegalArgumentException.class,
                () -> RepositoryUrlParser.parse(
                        "https://github.com/-invalid/Hello-World"
                )
        );
    }

    @Test
    void shouldRejectInvalidRepositoryName() {

        assertThrows(
                IllegalArgumentException.class,
                () -> RepositoryUrlParser.parse(
                        "https://github.com/octocat/invalid@repo"
                )
        );
    }
}