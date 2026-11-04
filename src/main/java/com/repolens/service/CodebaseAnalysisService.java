package com.repolens.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.repolens.client.GitHubApiClient;
import com.repolens.client.GitHubApiException;
import com.repolens.model.CodebaseAnalysis;
import com.repolens.model.CodebaseComposition;
import com.repolens.model.DirectoryStat;
import com.repolens.model.FileTypeStat;
import com.repolens.model.LargestFile;
import com.repolens.model.Repository;
import com.repolens.model.RepositoryFile;
import com.repolens.model.RepositoryTreeEntry;
import com.repolens.model.StructuralHotspot;
import com.repolens.util.RepositoryUrlParser;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CodebaseAnalysisService {

    private static final int MAX_EXPLORER_ENTRIES = 3000;
    private static final int MAX_DIRECTORY_RESULTS = 2000;
    private static final int MAX_HOTSPOTS = 100;
    private static final long LARGE_FILE_BYTES = 10L * 1024 * 1024;
    private static final long HIGH_FILE_COUNT = 500;
    private static final long DEFAULT_MAX_FILE_BYTES = 1024L * 1024;
    private static final long HARD_MAX_FILE_BYTES = 10L * 1024 * 1024;

    private static final Map<String, String> EXTENSION_LANGUAGES = Map.ofEntries(
            Map.entry(".java", "Java"), Map.entry(".kt", "Kotlin"),
            Map.entry(".kts", "Kotlin"), Map.entry(".js", "JavaScript"),
            Map.entry(".jsx", "JavaScript"), Map.entry(".mjs", "JavaScript"),
            Map.entry(".cjs", "JavaScript"), Map.entry(".ts", "TypeScript"),
            Map.entry(".tsx", "TypeScript"), Map.entry(".py", "Python"),
            Map.entry(".go", "Go"), Map.entry(".rs", "Rust"),
            Map.entry(".c", "C"), Map.entry(".h", "C"),
            Map.entry(".cc", "C++"), Map.entry(".cpp", "C++"),
            Map.entry(".cxx", "C++"), Map.entry(".hpp", "C++"),
            Map.entry(".cs", "C#"), Map.entry(".php", "PHP"),
            Map.entry(".rb", "Ruby"), Map.entry(".swift", "Swift"),
            Map.entry(".dart", "Dart"), Map.entry(".vue", "Vue"),
            Map.entry(".svelte", "Svelte"), Map.entry(".html", "HTML"),
            Map.entry(".htm", "HTML"), Map.entry(".css", "CSS"),
            Map.entry(".scss", "CSS"), Map.entry(".sh", "Shell"),
            Map.entry(".bash", "Shell"), Map.entry(".sql", "SQL"),
            Map.entry(".lua", "Lua"), Map.entry(".r", "R"),
            Map.entry(".ex", "Elixir"), Map.entry(".exs", "Elixir"),
            Map.entry(".scala", "Scala"), Map.entry(".groovy", "Groovy"),
            Map.entry(".gradle", "Groovy"), Map.entry(".pl", "Perl"),
            Map.entry(".pm", "Perl"), Map.entry(".hs", "Haskell"),
            Map.entry(".clj", "Clojure"), Map.entry(".cljs", "Clojure")
    );

    private static final Set<String> CONFIG_EXTENSIONS = Set.of(
            ".json", ".yaml", ".yml", ".toml", ".ini", ".xml",
            ".properties", ".conf", ".config", ".cfg", ".lock",
            ".editorconfig", ".gitignore", ".gitattributes",
            ".dockerignore", ".env.example"
    );
    private static final Set<String> DOCUMENTATION_EXTENSIONS = Set.of(
            ".md", ".mdx", ".rst", ".adoc", ".txt", ".org"
    );
    private static final Set<String> ASSET_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico",
            ".mp3", ".wav", ".mp4", ".mov", ".pdf", ".zip", ".gz",
            ".woff", ".woff2", ".ttf", ".otf", ".eot", ".jar", ".class"
    );
    private static final Set<String> CONFIG_FILENAMES = Set.of(
            ".gitignore", ".gitattributes", ".dockerignore",
            "dockerfile", "makefile", "cmakelists.txt"
    );
    private static final Set<String> GENERATED_DIRECTORIES = Set.of(
            "node_modules", "vendor", "dist", "build", "target",
            "coverage", "generated", ".git"
    );

    private final GitHubApiClient githubApiClient;
    private final RepositoryService repositoryService;
    private final long maxFileBytes;

    public CodebaseAnalysisService(
            GitHubApiClient githubApiClient,
            RepositoryService repositoryService
    ) {
        this.githubApiClient = githubApiClient;
        this.repositoryService = repositoryService;
        this.maxFileBytes = configuredMaxFileBytes();
    }

    public RepositoryFile getFile(
            String repositoryUrl,
            String path,
            String reference,
            String accessToken
    ) {
        var parsed = RepositoryUrlParser.parse(repositoryUrl);
        String safePath = validateFilePath(path);
        String selectedReference = reference == null || reference.isBlank()
                ? repositoryService.analyzeRepository(
                        repositoryUrl,
                        accessToken
                ).defaultBranch()
                : validateReference(reference);
        if (selectedReference == null || selectedReference.isBlank()) {
            throw new IllegalArgumentException(
                    "This repository has no default branch to inspect."
            );
        }

        JsonNode response = githubApiClient.getRepositoryFile(
                parsed.owner(),
                parsed.repository(),
                safePath,
                selectedReference,
                accessToken
        );
        if (response == null || !response.isObject()
                || !"file".equals(response.path("type").asText())) {
            throw new GitHubApiException(
                    "The selected path is not a repository file.",
                    404
            );
        }

        long size = response.path("size").asLong(-1);
        if (size < 0) {
            throw new GitHubApiException(
                    "GitHub did not return valid file size metadata.",
                    502
            );
        }
        if (size > maxFileBytes) {
            throw new FileTooLargeException(maxFileBytes);
        }

        String encoding = response.path("encoding").asText("");
        String encodedContent = response.path("content").asText("");
        if (!"base64".equalsIgnoreCase(encoding)) {
            throw new GitHubApiException(
                    "GitHub returned an unsupported file encoding.",
                    502
            );
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(
                    encodedContent.replaceAll("\\s", "")
            );
        } catch (IllegalArgumentException exception) {
            throw new GitHubApiException(
                    "GitHub returned invalid file content.",
                    502,
                    exception
            );
        }
        if (bytes.length > maxFileBytes) {
            throw new FileTooLargeException(maxFileBytes);
        }
        if (bytes.length != size) {
            throw new GitHubApiException(
                    "GitHub file size metadata did not match its content.",
                    502
            );
        }

        String content = null;
        boolean binary = containsNullByte(bytes);
        String contentEncoding = "binary";
        if (!binary) {
            try {
                content = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
                contentEncoding = "UTF-8";
            } catch (CharacterCodingException exception) {
                binary = true;
            }
        }

        return new RepositoryFile(
                safePath,
                fileName(safePath),
                size,
                languageFor(safePath),
                content,
                contentEncoding,
                binary
        );
    }

    private long configuredMaxFileBytes() {
        String configured = System.getProperty(
                "repolens.maxFileBytes",
                String.valueOf(DEFAULT_MAX_FILE_BYTES)
        );
        final long value;
        try {
            value = Long.parseLong(configured);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "repolens.maxFileBytes must be a whole number.",
                    exception
            );
        }
        if (value < 1 || value > HARD_MAX_FILE_BYTES) {
            throw new IllegalArgumentException(
                    "repolens.maxFileBytes must be between 1 and "
                            + HARD_MAX_FILE_BYTES + "."
            );
        }
        return value;
    }

    private String validateFilePath(String path) {
        if (path == null || path.isBlank() || path.length() > 1024
                || path.startsWith("/") || path.endsWith("/")
                || path.contains("\\") || path.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid repository file path.");
        }
        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException(
                        "Repository file path must not contain traversal segments."
                );
            }
        }
        return String.join("/", segments);
    }

    private String validateReference(String reference) {
        if (reference.length() > 255 || !reference.equals(reference.trim())
                || reference.startsWith("-") || reference.endsWith("/")
                || reference.startsWith("/") || reference.contains("//")
                || reference.contains("..") || reference.contains("@{")
                || reference.chars().anyMatch(Character::isISOControl)
                || reference.chars().anyMatch(character ->
                        " ~^:?*[\\".indexOf(character) >= 0)) {
            throw new IllegalArgumentException("Invalid branch or commit reference.");
        }
        for (String segment : reference.split("/", -1)) {
            if (segment.isEmpty() || segment.endsWith(".")
                    || segment.endsWith(".lock")) {
                throw new IllegalArgumentException(
                        "Invalid branch or commit reference."
                );
            }
        }
        return reference;
    }

    private boolean containsNullByte(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) return true;
        }
        return false;
    }

    private String languageFor(String path) {
        String extension = extension(fileName(path));
        return EXTENSION_LANGUAGES.get(extension);
    }

    public static final class FileTooLargeException extends RuntimeException {
        private final long maximumBytes;

        public FileTooLargeException(long maximumBytes) {
            super("File exceeds the " + maximumBytes + " byte viewer limit.");
            this.maximumBytes = maximumBytes;
        }

        public long maximumBytes() {
            return maximumBytes;
        }
    }

    public CodebaseAnalysis analyze(
            String repositoryUrl,
            String accessToken,
            int largestLimit
    ) {
        RepositoryUrlParser.ParsedRepository parsed =
                RepositoryUrlParser.parse(repositoryUrl);
        Repository repository = repositoryService.analyzeRepository(
                repositoryUrl,
                accessToken
        );
        String fullName = parsed.owner() + "/" + parsed.repository();
        String branch = repository.defaultBranch();
        if (branch == null || branch.isBlank()) {
            return unavailable(
                    fullName,
                    null,
                    "This repository has no default branch tree to inspect."
            );
        }

        JsonNode response;
        try {
            response = githubApiClient.getRepositoryTree(
                    parsed.owner(),
                    parsed.repository(),
                    branch,
                    accessToken
            );
        } catch (GitHubApiException exception) {
            if (exception.getStatusCode() == 404) {
                return unavailable(
                        fullName,
                        branch,
                        "GitHub did not provide a tree for the default branch. The repository may be empty or inaccessible."
                );
            }
            throw exception;
        }

        JsonNode tree = response == null ? null : response.path("tree");
        if (tree == null || !tree.isArray()) {
            return unavailable(
                    fullName,
                    branch,
                    "GitHub returned an invalid repository tree."
            );
        }

        boolean treeTruncated = response.path("truncated").asBoolean(false);
        Map<String, DirectoryCounts> directories = new HashMap<>();
        Map<String, Counts> extensions = new HashMap<>();
        Map<String, Counts> languages = new HashMap<>();
        Map<String, Counts> categories = new HashMap<>();
        List<FileEntry> files = new ArrayList<>();
        long knownBytes = 0;
        long missingSize = 0;
        int skippedPaths = 0;

        for (JsonNode item : tree) {
            String path = textOrNull(item.path("path"));
            String type = textOrNull(item.path("type"));
            if (!isSafePath(path) || type == null) {
                skippedPaths++;
                continue;
            }
            if ("tree".equals(type)) {
                directories.computeIfAbsent(path, ignored -> new DirectoryCounts());
                continue;
            }
            if (!"blob".equals(type)) continue;

            String name = fileName(path);
            String extension = extension(name);
            JsonNode sizeNode = item.path("size");
            Long size = sizeNode.isNumber() && sizeNode.asLong() >= 0
                    ? sizeNode.asLong()
                    : null;
            if (size == null) missingSize++;
            else knownBytes = Math.addExact(knownBytes, size);

            addCount(extensions, extension, size);
            addCount(categories, category(name, extension), size);
            String language = EXTENSION_LANGUAGES.get(extension);
            if (language != null) addCount(languages, language, size);

            String url = fileUrl(parsed.url(), branch, path);
            files.add(new FileEntry(
                    name, path, extension, parent(path), size, url
            ));

            String directory = parent(path);
            while (directory != null) {
                DirectoryCounts aggregate = directories.computeIfAbsent(
                        directory,
                        ignored -> new DirectoryCounts()
                );
                aggregate.fileCount++;
                if (size != null) {
                    aggregate.knownSizeBytes = Math.addExact(
                            aggregate.knownSizeBytes,
                            size
                    );
                }
                directory = parent(directory);
            }
            if (!path.contains("/")) {
                DirectoryCounts root = directories.computeIfAbsent(
                        "(root)",
                        ignored -> new DirectoryCounts()
                );
                root.fileCount++;
                if (size != null) {
                    root.knownSizeBytes = Math.addExact(root.knownSizeBytes, size);
                }
            }
        }

        List<DirectoryStat> directoryStats = makeDirectoryStats(
                directories,
                files.size(),
                knownBytes
        );
        List<FileEntry> largest = files.stream()
                .filter(file -> file.size != null)
                .sorted(Comparator.comparingLong(
                        (FileEntry file) -> file.size
                ).reversed().thenComparing(file -> file.path))
                .limit(largestLimit)
                .toList();
        List<RepositoryTreeEntry> explorer = makeExplorerEntries(
                files,
                directories,
                MAX_EXPLORER_ENTRIES
        );
        long actualTreeEntries = tree.size();
        boolean explorerTruncated = actualTreeEntries > explorer.size();
        List<StructuralHotspot> hotspots = new ArrayList<>(findHotspots(
                files,
                directoryStats,
                knownBytes,
                treeTruncated
        ));
        if (skippedPaths > 0) {
            hotspots.add(new StructuralHotspot(
                    "Skipped malformed paths",
                    null,
                    skippedPaths + " unsafe or malformed tree paths were excluded.",
                    "Review the upstream GitHub tree response if entries appear missing."
            ));
        }
        if (treeTruncated) {
            hotspots.add(new StructuralHotspot(
                    "Partial tree",
                    null,
                    "GitHub marked the recursive tree as truncated; all returned totals are partial.",
                    "Do not interpret the displayed values as complete repository totals."
            ));
        }

        CodebaseComposition composition = new CodebaseComposition(
                files.size(),
                files.size() - missingSize,
                missingSize,
                knownBytes,
                typeStats(extensions),
                typeStats(languages),
                typeStats(categories)
        );
        return new CodebaseAnalysis(
                fullName,
                branch,
                true,
                treeTruncated,
                explorerTruncated,
                null,
                files.size(),
                directories.keySet().stream()
                        .filter(path -> !"(root)".equals(path))
                        .count(),
                knownBytes,
                missingSize,
                composition,
                explorer,
                directoryStats.stream().limit(MAX_DIRECTORY_RESULTS).toList(),
                largest.stream().map(FileEntry::toLargestFile).toList(),
                hotspots.stream().limit(MAX_HOTSPOTS).toList(),
                "GitHub recursive trees can be truncated. Git does not track empty directories. Sizes are reported blob sizes, not checkout size or line counts. File categories and languages are inferred from extensions and may not match GitHub Linguist."
        );
    }

    private CodebaseAnalysis unavailable(
            String fullName,
            String branch,
            String reason
    ) {
        return new CodebaseAnalysis(
                fullName,
                branch,
                false,
                false,
                false,
                reason,
                0,
                0,
                0,
                0,
                new CodebaseComposition(
                        0, 0, 0, 0, List.of(), List.of(), List.of()
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "No tree analysis is available."
        );
    }

    private List<DirectoryStat> makeDirectoryStats(
            Map<String, DirectoryCounts> directories,
            long totalFiles,
            long totalKnownBytes
    ) {
        return directories.entrySet().stream()
                .map(entry -> {
                    DirectoryCounts counts = entry.getValue();
                    double fileShare = totalFiles == 0
                            ? 0
                            : Math.round(
                                    counts.fileCount * 10000.0 / totalFiles
                            ) / 100.0;
                    Double sizeShare = totalKnownBytes == 0
                            ? null
                            : Math.round(
                                    counts.knownSizeBytes * 10000.0 / totalKnownBytes
                            ) / 100.0;
                    return new DirectoryStat(
                            entry.getKey(),
                            counts.fileCount,
                            counts.knownSizeBytes,
                            fileShare,
                            sizeShare
                    );
                })
                .sorted(Comparator.comparingLong(DirectoryStat::knownSizeBytes)
                        .reversed()
                        .thenComparing(DirectoryStat::path))
                .toList();
    }

    private List<RepositoryTreeEntry> makeExplorerEntries(
            List<FileEntry> files,
            Map<String, DirectoryCounts> directories,
            int limit
    ) {
        List<RepositoryTreeEntry> entries = new ArrayList<>(limit);
        Map<String, FileEntry> filesByPath = files.stream()
                .collect(Collectors.toMap(
                        file -> file.path,
                        file -> file,
                        (first, ignored) -> first
                ));
        Set<String> paths = new LinkedHashSet<>();
        directories.keySet().stream()
                .filter(path -> !"(root)".equals(path))
                .sorted()
                .forEach(paths::add);
        files.stream()
                .map(file -> file.path)
                .sorted()
                .forEach(paths::add);
        for (String path : paths) {
            if (entries.size() == limit) break;
            FileEntry file = filesByPath.get(path);
            if (file != null) {
                entries.add(new RepositoryTreeEntry(
                        file.path, file.name, "file", file.extension,
                        file.size, file.url
                ));
            } else {
                entries.add(new RepositoryTreeEntry(
                        path, fileName(path), "directory", null, null, null
                ));
            }
        }
        return entries;
    }

    private List<StructuralHotspot> findHotspots(
            List<FileEntry> files,
            List<DirectoryStat> directories,
            long knownBytes,
            boolean treeTruncated
    ) {
        List<StructuralHotspot> result = new ArrayList<>();
        Set<String> generatedPaths = new HashSet<>();
        for (DirectoryStat directory : directories) {
            if (directory.fileCount() == 1) {
                result.add(new StructuralHotspot(
                        "Single-file directory",
                        directory.path(),
                        "One tracked file is nested under this directory.",
                        "A small directory is not inherently problematic; inspect only if the structure is surprising."
                ));
            }
            if (directory.fileCount() >= HIGH_FILE_COUNT) {
                result.add(new StructuralHotspot(
                        "High file count",
                        directory.path(),
                        directory.fileCount() + " tracked files are nested here."
                                + partialNote(treeTruncated),
                        "Review whether the directory remains easy to navigate."
                ));
            }
            if (directory.sizeSharePercent() != null
                    && directory.sizeSharePercent() > 50) {
                result.add(new StructuralHotspot(
                        "Size concentration",
                        directory.path(),
                        String.format(
                                Locale.ROOT,
                                "%.2f%% of known blob bytes are under this directory.",
                                directory.sizeSharePercent()
                        ) + partialNote(treeTruncated),
                        "Inspect the largest files if this concentration is unexpected."
                ));
            }
        }
        for (FileEntry file : files) {
            int depth = file.path.split("/").length;
            if (depth >= 12) {
                result.add(new StructuralHotspot(
                        "Deep nesting",
                        file.path,
                        "This file is nested " + depth + " path levels deep.",
                        "Check whether the nesting is intentional."
                ));
            }
            if (file.size != null && file.size > LARGE_FILE_BYTES) {
                result.add(new StructuralHotspot(
                        "Large file",
                        file.path,
                        "The reported Git blob size is " + file.size
                                + " bytes; the observation threshold is "
                                + LARGE_FILE_BYTES + " bytes.",
                        "File size alone does not demonstrate a performance or maintainability problem."
                ));
            }
            for (String segment : file.path.split("/")) {
                String normalized = segment.toLowerCase(Locale.ROOT);
                if (GENERATED_DIRECTORIES.contains(normalized)) {
                    String generatedPath = file.path.substring(
                            0,
                            file.path.indexOf(segment) + segment.length()
                    );
                    if (generatedPaths.add(generatedPath)) {
                        result.add(new StructuralHotspot(
                                "Recognizable generated/vendor path",
                                generatedPath,
                                "The path includes a commonly generated or vendor directory name.",
                                "Confirm generated or vendored content is intended to be tracked."
                        ));
                    }
                    break;
                }
            }
        }
        return result.stream().distinct().limit(MAX_HOTSPOTS).toList();
    }

    private String partialNote(boolean truncated) {
        return truncated
                ? " GitHub truncated the tree, so this count may be incomplete."
                : "";
    }

    private void addCount(Map<String, Counts> map, String key, Long size) {
        Counts counts = map.computeIfAbsent(key, ignored -> new Counts());
        counts.files++;
        if (size != null) counts.size = Math.addExact(counts.size, size);
    }

    private List<FileTypeStat> typeStats(Map<String, Counts> counts) {
        return counts.entrySet().stream()
                .map(entry -> new FileTypeStat(
                        entry.getKey(),
                        entry.getValue().files,
                        entry.getValue().size
                ))
                .sorted(Comparator.comparingLong(FileTypeStat::fileCount)
                        .reversed().thenComparing(FileTypeStat::name))
                .toList();
    }

    private String category(String name, String extension) {
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (EXTENSION_LANGUAGES.containsKey(extension)) return "Source code";
        if (CONFIG_EXTENSIONS.contains(extension)
                || lowerName.endsWith(".env.example")
                || CONFIG_FILENAMES.contains(lowerName)) {
            return "Configuration";
        }
        if (DOCUMENTATION_EXTENSIONS.contains(extension)) {
            return "Documentation";
        }
        if (ASSET_EXTENSIONS.contains(extension)) return "Assets";
        return "Other";
    }

    private String extension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.equals("dockerfile") || lower.equals("makefile")) {
            return "[no extension]";
        }
        int dot = lower.lastIndexOf('.');
        if (dot <= 0 || dot == lower.length() - 1) {
            return "[no extension]";
        }
        return lower.substring(dot);
    }

    private boolean isSafePath(String path) {
        if (path == null || path.isBlank() || path.startsWith("/")
                || path.contains("\\") || path.indexOf('\0') >= 0) {
            return false;
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    private String fileUrl(String repositoryUrl, String branch, String path) {
        String encodedBranch = encodePathSegment(branch);
        String encodedPath = Arrays.stream(path.split("/"))
                .map(this::encodePathSegment)
                .collect(Collectors.joining("/"));
        return repositoryUrl + "/blob/" + encodedBranch + "/" + encodedPath;
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private String textOrNull(JsonNode value) {
        return value.isTextual() ? value.asText() : null;
    }

    private String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? null : path.substring(0, slash);
    }

    private static final class Counts {
        private long files;
        private long size;
    }

    private static final class DirectoryCounts {
        private long fileCount;
        private long knownSizeBytes;
    }

    private record FileEntry(
            String name,
            String path,
            String extension,
            String directory,
            Long size,
            String url
    ) {
        private LargestFile toLargestFile() {
            return new LargestFile(
                    name,
                    path,
                    extension,
                    directory == null ? "(root)" : directory,
                    size,
                    url
            );
        }
    }
}
