package com.repolens.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.repolens.client.GitHubApiClient;
import com.repolens.client.GitHubApiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CodebaseAnalysisServiceTest {

    private static final String REPOSITORY_URL =
            "https://github.com/octocat/Hello-World";
    private final ObjectMapper mapper = new ObjectMapper();
    private FakeGitHubApiClient github;
    private CodebaseAnalysisService service;

    @BeforeEach
    void setUp() {
        github = new FakeGitHubApiClient();
        RepositoryService repositoryService = new RepositoryService(github);
        service = new CodebaseAnalysisService(github, repositoryService);
        github.repository = repository("main");
        github.tree = tree(
                new TreeItem("src", "tree", null),
                new TreeItem("src/main", "tree", null),
                new TreeItem("src/main/java", "tree", null),
                new TreeItem("assets", "tree", null),
                new TreeItem("README.md", "blob", 50L),
                new TreeItem("src/main/java/App.java", "blob", 200L),
                new TreeItem("assets/logo.png", "blob", null),
                new TreeItem(".gitignore", "blob", 10L)
        );
    }

    @Test
    void aggregatesNestedDirectoriesFilesAndTypes() {
        var result = service.analyze(REPOSITORY_URL, "test-token", 10);

        assertTrue(result.available());
        assertEquals(4, result.totalTrackedFiles());
        assertEquals(4, result.totalDirectories());
        assertEquals(260, result.totalKnownBlobSizeBytes());
        assertEquals(1, result.filesMissingSize());
        assertEquals(3, result.composition().filesWithKnownSize());
        assertEquals(4, result.composition().extensions().stream()
                .mapToLong(stat -> stat.fileCount()).sum());
        assertTrue(result.composition().languages().stream()
                .anyMatch(language -> language.name().equals("Java")
                        && language.fileCount() == 1));
        assertTrue(result.composition().categories().stream()
                .anyMatch(category -> category.name().equals("Assets")
                        && category.fileCount() == 1));
        assertTrue(result.treeEntries().stream()
                .anyMatch(entry -> entry.path().equals("src/main/java/App.java")));
    }

    @Test
    void sortsLargestFilesAndAggregatesDirectoryShares() {
        var result = service.analyze(REPOSITORY_URL, "test-token", 2);

        assertEquals(2, result.largestFiles().size());
        assertEquals("src/main/java/App.java", result.largestFiles().get(0).path());
        assertEquals(200L, result.largestFiles().get(0).sizeBytes());
        var source = result.directories().stream()
                .filter(directory -> directory.path().equals("src"))
                .findFirst()
                .orElseThrow();
        assertEquals(25.0, source.fileSharePercent(), 0.001);
        assertEquals(76.92, source.sizeSharePercent(), 0.01);
    }

    @Test
    void reportsTruncatedTreesAsPartial() {
        ((ObjectNode) github.tree).put("truncated", true);

        var result = service.analyze(REPOSITORY_URL, "test-token", 10);

        assertTrue(result.treeTruncated());
        assertTrue(result.hotspots().stream()
                .anyMatch(hotspot -> hotspot.category().equals("Partial tree")));
    }

    @Test
    void handlesAnEmptyTreeAsAvailableWithZeroFiles() {
        github.tree = mapper.createObjectNode()
                .put("truncated", false)
                .set("tree", mapper.createArrayNode());

        var result = service.analyze(REPOSITORY_URL, "test-token", 10);

        assertTrue(result.available());
        assertEquals(0, result.totalTrackedFiles());
        assertEquals(0, result.totalDirectories());
        assertEquals(0, result.totalKnownBlobSizeBytes());
        assertTrue(result.treeEntries().isEmpty());
    }

    @Test
    void reportsMissingDefaultBranchAsUnavailable() {
        github.repository = repository(null);

        var result = service.analyze(REPOSITORY_URL, "test-token", 10);

        assertFalse(result.available());
        assertEquals(0, result.totalTrackedFiles());
        assertTrue(result.unavailableReason().contains("no default branch"));
        assertEquals(0, github.treeRequests);
    }

    @Test
    void treatsGitHubMissingTreeAsUnavailableAndPropagatesRateLimits() {
        github.treeFailure = new GitHubApiException("Missing tree.", 404);
        var unavailable = service.analyze(REPOSITORY_URL, "test-token", 10);
        assertFalse(unavailable.available());

        github.treeFailure = new GitHubApiException("Rate limited.", 403);
        GitHubApiException error = assertThrows(
                GitHubApiException.class,
                () -> service.analyze(REPOSITORY_URL, "test-token", 10)
        );
        assertEquals(403, error.getStatusCode());
    }

    @Test
    void excludesUnsafePathsAndHandlesUnknownExtensions() {
        github.tree = tree(
                new TreeItem("../outside.java", "blob", 500L),
                new TreeItem("src/../outside.java", "blob", 500L),
                new TreeItem("data/mystery.xyz", "blob", 15L)
        );

        var result = service.analyze(REPOSITORY_URL, "test-token", 10);

        assertEquals(1, result.totalTrackedFiles());
        assertEquals(".xyz", result.composition().extensions().get(0).name());
        assertTrue(result.hotspots().stream()
                .anyMatch(hotspot -> hotspot.category().equals("Skipped malformed paths")));
    }

    @Test
    void detectsLargeFilesAndRecognizableGeneratedPathsWithEvidence() {
        github.tree = tree(
                new TreeItem("vendor/lib/data.bin", "blob", 11L * 1024 * 1024),
                new TreeItem("src/deep/" + "a/".repeat(10) + "Main.java", "blob", 10L)
        );

        var result = service.analyze(REPOSITORY_URL, "test-token", 10);

        assertTrue(result.hotspots().stream()
                .anyMatch(hotspot -> hotspot.category().equals("Large file")));
        assertTrue(result.hotspots().stream()
                .anyMatch(hotspot -> hotspot.category().equals("Recognizable generated/vendor path")));
        assertTrue(result.hotspots().stream()
                .anyMatch(hotspot -> hotspot.category().equals("Deep nesting")));
    }

    private JsonNode repository(String branch) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", 1);
        node.put("name", "Hello-World");
        node.put("full_name", "octocat/Hello-World");
        node.put("html_url", REPOSITORY_URL);
        if (branch == null) node.putNull("default_branch");
        else node.put("default_branch", branch);
        return node;
    }

    private ObjectNode tree(TreeItem... items) {
        ObjectNode root = mapper.createObjectNode();
        root.put("truncated", false);
        ArrayNode values = root.putArray("tree");
        for (TreeItem item : items) {
            ObjectNode node = values.addObject();
            node.put("path", item.path());
            node.put("type", item.type());
            if (item.size() == null) node.putNull("size");
            else node.put("size", item.size());
        }
        return root;
    }

    private record TreeItem(String path, String type, Long size) {}

    private static final class FakeGitHubApiClient extends GitHubApiClient {
        private JsonNode repository;
        private JsonNode tree;
        private GitHubApiException treeFailure;
        private int treeRequests;

        @Override
        public JsonNode getRepository(String owner, String name, String token) {
            return repository;
        }

        @Override
        public JsonNode getRepositoryTree(
                String owner,
                String name,
                String branch,
                String token
        ) {
            treeRequests++;
            if (treeFailure != null) throw treeFailure;
            return tree;
        }
    }
}
