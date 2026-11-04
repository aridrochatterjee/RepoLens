package com.repolens.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.regex.Pattern;

public final class RepositoryUrlParser {

    private static final String GITHUB_HOST = "github.com";

    private static final Pattern OWNER_PATTERN =
            Pattern.compile("^[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,37}[a-zA-Z0-9])?$");

    private static final Pattern REPOSITORY_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._-]{1,100}$");

    private RepositoryUrlParser() {
        // Utility class. Prevent instantiation.
    }

    /**
     * Parses and validates a public GitHub repository URL.
     *
     * Accepted examples:
     * https://github.com/owner/repository
     * https://github.com/owner/repository/
     * https://github.com/owner/repository.git
     *
     * @param repositoryUrl GitHub repository URL
     * @return parsed repository information
     * @throws IllegalArgumentException if the URL is invalid
     */
    public static ParsedRepository parse(String repositoryUrl) {

        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Repository URL cannot be empty."
            );
        }

        String input = repositoryUrl.trim();

        final URI uri;

        try {
            uri = new URI(input);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(
                    "Invalid repository URL format.",
                    exception
            );
        }

        validateProtocol(uri);
        validateHost(uri);
        validateUrlComponents(uri);

        String path = uri.getRawPath();

        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException(
                    "Repository owner and name are required."
            );
        }

        // Reject encoded paths to avoid ambiguous path interpretation.
        if (path.contains("%")) {
            throw new IllegalArgumentException(
                    "Encoded repository paths are not supported."
            );
        }

        // Remove trailing slashes.
        path = path.replaceAll("/+$", "");

        // Remove optional Git clone suffix.
        if (path.endsWith(".git")) {
            path = path.substring(0, path.length() - 4);
        }

        String[] segments = path.substring(1).split("/", -1);

        if (segments.length != 2
                || segments[0].isBlank()
                || segments[1].isBlank()) {

            throw new IllegalArgumentException(
                    "Expected URL format: https://github.com/owner/repository"
            );
        }

        String owner = segments[0];
        String repository = segments[1];

        validateOwner(owner);
        validateRepository(repository);

        String normalizedUrl =
                "https://" + GITHUB_HOST + "/" + owner + "/" + repository;

        return new ParsedRepository(
                owner,
                repository,
                normalizedUrl
        );
    }

    private static void validateProtocol(URI uri) {

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(
                    "Only HTTPS GitHub URLs are supported."
            );
        }
    }

    private static void validateHost(URI uri) {

        if (!GITHUB_HOST.equalsIgnoreCase(uri.getHost())) {
            throw new IllegalArgumentException(
                    "Only github.com repository URLs are supported."
            );
        }
    }

    private static void validateUrlComponents(URI uri) {

        if (uri.getPort() != -1
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {

            throw new IllegalArgumentException(
                    "Repository URL contains unsupported components."
            );
        }
    }

    private static void validateOwner(String owner) {

        if (!OWNER_PATTERN.matcher(owner).matches()) {
            throw new IllegalArgumentException(
                    "Invalid GitHub repository owner."
            );
        }
    }

    private static void validateRepository(String repository) {

        if (!REPOSITORY_PATTERN.matcher(repository).matches()
                || repository.equals(".")
                || repository.equals("..")) {

            throw new IllegalArgumentException(
                    "Invalid GitHub repository name."
            );
        }
    }

    /**
     * Immutable representation of a parsed GitHub repository.
     */
    public record ParsedRepository(
            String owner,
            String repository,
            String url
    ) {

        public ParsedRepository {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(repository);
            Objects.requireNonNull(url);
        }
    }
}