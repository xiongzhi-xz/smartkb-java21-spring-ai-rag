package com.smartkb.agent.application;

import com.smartkb.agent.domain.CodeContextException;
import com.smartkb.agent.domain.CodeChunkRequest;
import com.smartkb.agent.domain.CodeChunkResponse;
import com.smartkb.agent.domain.CodeDiffRequest;
import com.smartkb.agent.domain.CodeDiffResponse;
import com.smartkb.agent.domain.CodeSearchRequest;
import com.smartkb.agent.domain.CodeSearchResponse;
import com.smartkb.agent.domain.CodeSemanticSearchRequest;
import com.smartkb.agent.domain.CodeSemanticSearchResponse;
import com.smartkb.agent.domain.CodeTreeRequest;
import com.smartkb.agent.domain.CodeTreeResponse;
import com.smartkb.agent.domain.ProjectIntakeResponse;
import com.smartkb.agent.infrastructure.filesystem.ProjectPathGuard;
import com.smartkb.agent.infrastructure.git.GitDiffReader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Read-only code context index and keyword search.
 */
@Service
public class CodeContextService {

    private static final int DEFAULT_MAX_FILES = 500;
    private static final int DEFAULT_MAX_DEPTH = 8;
    private static final int DEFAULT_MAX_RESULTS = 100;
    private static final int DEFAULT_MAX_FILE_BYTES = 1_048_576;
    private static final int DEFAULT_MAX_DIFF_LINES = 200;
    private static final int DEFAULT_MAX_CHUNKS = 100;
    private static final int DEFAULT_MAX_CHUNK_CHARS = 2_000;
    private static final int DEFAULT_MAX_SEMANTIC_RESULTS = 20;
    private static final Pattern HUNK_HEADER = Pattern.compile("@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*");
    private static final Pattern TERM_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");

    private final ProjectPathGuard pathGuard;
    private final GitDiffReader gitDiffReader;

    public CodeContextService(ProjectPathGuard pathGuard, GitDiffReader gitDiffReader) {
        this.pathGuard = pathGuard;
        this.gitDiffReader = gitDiffReader;
    }

    public CodeTreeResponse tree(CodeTreeRequest request) {
        CodeTreeRequest safeRequest = request == null
                ? new CodeTreeRequest(null, null, null)
                : request;
        Path root = pathGuard.validateProjectRoot(safeRequest.rootPath());
        int maxFiles = positiveOrDefault(safeRequest.maxFiles(), DEFAULT_MAX_FILES);
        int maxDepth = positiveOrDefault(safeRequest.maxDepth(), DEFAULT_MAX_DEPTH);

        List<CodeTreeResponse.CodeFile> files = new ArrayList<>();
        List<ProjectIntakeResponse.SkippedFile> skippedFiles = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        scanTree(root, root, 0, maxDepth, maxFiles, files, skippedFiles, warnings);
        return new CodeTreeResponse(
                true,
                normalizePath(root),
                files,
                skippedFiles,
                warnings
        );
    }

    public CodeSearchResponse search(CodeSearchRequest request) {
        CodeSearchRequest safeRequest = request == null
                ? new CodeSearchRequest(null, null, null, null)
                : request;
        Path root = pathGuard.validateProjectRoot(safeRequest.rootPath());
        String query = normalizeQuery(safeRequest.query());
        int maxResults = positiveOrDefault(safeRequest.maxResults(), DEFAULT_MAX_RESULTS);
        int maxFileBytes = positiveOrDefault(safeRequest.maxFileBytes(), DEFAULT_MAX_FILE_BYTES);

        List<CodeTreeResponse.CodeFile> files = new ArrayList<>();
        List<CodeSearchResponse.CodeMatch> matches = new ArrayList<>();
        List<ProjectIntakeResponse.SkippedFile> skippedFiles = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        scanTree(root, root, 0, DEFAULT_MAX_DEPTH, DEFAULT_MAX_FILES, files, skippedFiles, warnings);

        for (CodeTreeResponse.CodeFile file : files) {
            if (matches.size() >= maxResults) {
                warnings.add("max results reached");
                break;
            }
            Path path = root.resolve(file.path()).normalize();
            searchFile(root, path, query, maxResults, maxFileBytes, matches, skippedFiles, warnings);
        }

        return new CodeSearchResponse(
                true,
                normalizePath(root),
                query,
                matches,
                skippedFiles,
                warnings
        );
    }

    public CodeDiffResponse diff(CodeDiffRequest request) {
        CodeDiffRequest safeRequest = request == null
                ? new CodeDiffRequest(null, null, null)
                : request;
        Path root = pathGuard.validateProjectRoot(safeRequest.rootPath());
        String query = safeRequest.query() == null || safeRequest.query().isBlank()
                ? null
                : safeRequest.query().strip();
        int maxLines = positiveOrDefault(safeRequest.maxLines(), DEFAULT_MAX_DIFF_LINES);
        List<ProjectIntakeResponse.SkippedFile> skippedFiles = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (!gitDiffReader.isGitRepository(root)) {
            warnings.add("not a git repository");
            return new CodeDiffResponse(true, normalizePath(root), false, query, List.of(), skippedFiles, warnings);
        }

        Map<String, List<CodeDiffResponse.DiffLine>> linesByPath = new LinkedHashMap<>();
        for (String relativePath : gitDiffReader.changedFiles(root, warnings)) {
            Path path = root.resolve(relativePath).normalize();
            if (!path.startsWith(root) || pathGuard.shouldSkip(root, path)) {
                String reason = path.startsWith(root) ? pathGuard.skipReason(root, path) : "outside project root";
                skippedFiles.add(new ProjectIntakeResponse.SkippedFile(relativePath, reason));
                continue;
            }
            addDiffLines(relativePath, gitDiffReader.diff(root, relativePath, false), query, maxLines, linesByPath);
            addDiffLines(relativePath, gitDiffReader.diff(root, relativePath, true), query, maxLines, linesByPath);
            if (totalLines(linesByPath) >= maxLines) {
                warnings.add("max diff lines reached");
                break;
            }
        }

        List<CodeDiffResponse.DiffFile> files = linesByPath.entrySet().stream()
                .map(entry -> new CodeDiffResponse.DiffFile(entry.getKey(), entry.getValue()))
                .toList();
        return new CodeDiffResponse(true, normalizePath(root), true, query, files, skippedFiles, warnings);
    }

    public CodeChunkResponse chunks(CodeChunkRequest request) {
        CodeChunkRequest safeRequest = request == null
                ? new CodeChunkRequest(null, null, null, null)
                : request;
        Path root = pathGuard.validateProjectRoot(safeRequest.rootPath());
        int maxChunks = positiveOrDefault(safeRequest.maxChunks(), DEFAULT_MAX_CHUNKS);
        int maxFileBytes = positiveOrDefault(safeRequest.maxFileBytes(), DEFAULT_MAX_FILE_BYTES);
        int maxChunkChars = positiveOrDefault(safeRequest.maxChunkChars(), DEFAULT_MAX_CHUNK_CHARS);

        List<CodeTreeResponse.CodeFile> files = new ArrayList<>();
        List<CodeChunkResponse.CodeChunk> chunks = new ArrayList<>();
        List<ProjectIntakeResponse.SkippedFile> skippedFiles = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        scanTree(root, root, 0, DEFAULT_MAX_DEPTH, DEFAULT_MAX_FILES, files, skippedFiles, warnings);

        for (CodeTreeResponse.CodeFile file : files) {
            if (chunks.size() >= maxChunks) {
                warnings.add("max chunks reached");
                break;
            }
            Path path = root.resolve(file.path()).normalize();
            addChunks(root, path, maxChunks, maxFileBytes, maxChunkChars, chunks, skippedFiles, warnings);
        }

        return new CodeChunkResponse(true, normalizePath(root), chunks, skippedFiles, warnings);
    }

    public CodeSemanticSearchResponse semanticSearch(CodeSemanticSearchRequest request) {
        CodeSemanticSearchRequest safeRequest = request == null
                ? new CodeSemanticSearchRequest(null, null, null, null, null)
                : request;
        Path root = pathGuard.validateProjectRoot(safeRequest.rootPath());
        String query = normalizeQuery(safeRequest.query());
        int maxResults = positiveOrDefault(safeRequest.maxResults(), DEFAULT_MAX_SEMANTIC_RESULTS);
        int maxFileBytes = positiveOrDefault(safeRequest.maxFileBytes(), DEFAULT_MAX_FILE_BYTES);
        int maxChunkChars = positiveOrDefault(safeRequest.maxChunkChars(), DEFAULT_MAX_CHUNK_CHARS);

        CodeChunkResponse chunkResponse = chunks(new CodeChunkRequest(
                root.toString(),
                Math.max(DEFAULT_MAX_CHUNKS, maxResults * 10),
                maxFileBytes,
                maxChunkChars
        ));
        List<String> terms = extractTerms(query);
        List<CodeSemanticSearchResponse.SemanticMatch> matches = chunkResponse.chunks().stream()
                .map(chunk -> scoreChunk(chunk, terms))
                .filter(match -> match.score() > 0)
                .sorted(Comparator
                        .comparingInt(CodeSemanticSearchResponse.SemanticMatch::score)
                        .reversed()
                        .thenComparing(CodeSemanticSearchResponse.SemanticMatch::path)
                        .thenComparingInt(CodeSemanticSearchResponse.SemanticMatch::startLine))
                .limit(maxResults)
                .toList();

        return new CodeSemanticSearchResponse(
                true,
                normalizePath(root),
                query,
                matches,
                chunkResponse.skippedFiles(),
                chunkResponse.warnings()
        );
    }

    private void scanTree(
            Path root,
            Path current,
            int depth,
            int maxDepth,
            int maxFiles,
            List<CodeTreeResponse.CodeFile> files,
            List<ProjectIntakeResponse.SkippedFile> skippedFiles,
            List<String> warnings
    ) {
        if (files.size() >= maxFiles) {
            return;
        }
        if (depth > maxDepth) {
            return;
        }
        if (!current.equals(root) && pathGuard.shouldSkip(root, current)) {
            skippedFiles.add(new ProjectIntakeResponse.SkippedFile(
                    pathGuard.toRelative(root, current),
                    pathGuard.skipReason(root, current)
            ));
            return;
        }
        if (Files.isSymbolicLink(current)) {
            skippedFiles.add(new ProjectIntakeResponse.SkippedFile(
                    pathGuard.toRelative(root, current),
                    "symbolic link"
            ));
            return;
        }
        if (Files.isRegularFile(current)) {
            addFile(root, current, maxFiles, files, warnings);
            return;
        }
        if (!Files.isDirectory(current)) {
            return;
        }

        try (Stream<Path> children = Files.list(current)) {
            List<Path> sorted = children
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
            for (Path child : sorted) {
                if (files.size() >= maxFiles) {
                    warnings.add("max files reached");
                    return;
                }
                scanTree(root, child, depth + 1, maxDepth, maxFiles, files, skippedFiles, warnings);
            }
        } catch (IOException exception) {
            warnings.add("failed to read directory: " + pathGuard.toRelative(root, current));
        }
    }

    private void addFile(
            Path root,
            Path file,
            int maxFiles,
            List<CodeTreeResponse.CodeFile> files,
            List<String> warnings
    ) {
        if (files.size() >= maxFiles) {
            warnings.add("max files reached");
            return;
        }
        try {
            files.add(new CodeTreeResponse.CodeFile(
                    pathGuard.toRelative(root, file),
                    Files.size(file),
                    extension(file)
            ));
        } catch (IOException exception) {
            warnings.add("failed to read file metadata: " + pathGuard.toRelative(root, file));
        }
    }

    private void searchFile(
            Path root,
            Path file,
            String query,
            int maxResults,
            int maxFileBytes,
            List<CodeSearchResponse.CodeMatch> matches,
            List<ProjectIntakeResponse.SkippedFile> skippedFiles,
            List<String> warnings
    ) {
        String relative = pathGuard.toRelative(root, file);
        try {
            long size = Files.size(file);
            if (size > maxFileBytes) {
                skippedFiles.add(new ProjectIntakeResponse.SkippedFile(relative, "file too large"));
                return;
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int index = 0; index < lines.size(); index++) {
                if (matches.size() >= maxResults) {
                    return;
                }
                String line = lines.get(index);
                if (line.contains(query)) {
                    matches.add(new CodeSearchResponse.CodeMatch(relative, index + 1, line.strip()));
                }
            }
        } catch (MalformedInputException exception) {
            skippedFiles.add(new ProjectIntakeResponse.SkippedFile(relative, "non utf-8 file"));
        } catch (IOException exception) {
            warnings.add("failed to search file: " + relative);
        }
    }

    private void addChunks(
            Path root,
            Path file,
            int maxChunks,
            int maxFileBytes,
            int maxChunkChars,
            List<CodeChunkResponse.CodeChunk> chunks,
            List<ProjectIntakeResponse.SkippedFile> skippedFiles,
            List<String> warnings
    ) {
        String relative = pathGuard.toRelative(root, file);
        try {
            if (Files.size(file) > maxFileBytes) {
                skippedFiles.add(new ProjectIntakeResponse.SkippedFile(relative, "file too large"));
                return;
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            int startLine = 1;
            StringBuilder current = new StringBuilder();
            for (int index = 0; index < lines.size(); index++) {
                if (chunks.size() >= maxChunks) {
                    return;
                }
                String line = lines.get(index);
                if (!current.isEmpty() && current.length() + line.length() + 1 > maxChunkChars) {
                    chunks.add(new CodeChunkResponse.CodeChunk(relative, startLine, index, current.toString().strip()));
                    current.setLength(0);
                    startLine = index + 1;
                }
                current.append(line).append('\n');
            }
            if (!current.isEmpty() && chunks.size() < maxChunks) {
                chunks.add(new CodeChunkResponse.CodeChunk(relative, startLine, lines.size(), current.toString().strip()));
            }
        } catch (MalformedInputException exception) {
            skippedFiles.add(new ProjectIntakeResponse.SkippedFile(relative, "non utf-8 file"));
        } catch (IOException exception) {
            warnings.add("failed to chunk file: " + relative);
        }
    }

    private void addDiffLines(
            String relativePath,
            String diffOutput,
            String query,
            int maxLines,
            Map<String, List<CodeDiffResponse.DiffLine>> linesByPath
    ) {
        if (diffOutput == null || diffOutput.isBlank()) {
            return;
        }
        int oldLine = 0;
        int newLine = 0;
        for (String rawLine : diffOutput.lines().toList()) {
            if (totalLines(linesByPath) >= maxLines) {
                return;
            }
            Matcher matcher = HUNK_HEADER.matcher(rawLine);
            if (matcher.matches()) {
                oldLine = Integer.parseInt(matcher.group(1));
                newLine = Integer.parseInt(matcher.group(2));
                continue;
            }
            if (rawLine.startsWith("+++") || rawLine.startsWith("---")
                    || rawLine.startsWith("diff --git") || rawLine.startsWith("index ")) {
                continue;
            }
            CodeDiffResponse.DiffLine line = parseDiffLine(rawLine, oldLine, newLine);
            if (line == null) {
                continue;
            }
            if ("add".equals(line.type()) || "context".equals(line.type())) {
                newLine++;
            }
            if ("delete".equals(line.type()) || "context".equals(line.type())) {
                oldLine++;
            }
            if (query != null && !line.content().contains(query)) {
                continue;
            }
            linesByPath.computeIfAbsent(relativePath, ignored -> new ArrayList<>()).add(line);
        }
    }

    private CodeDiffResponse.DiffLine parseDiffLine(String rawLine, int oldLine, int newLine) {
        if (rawLine.startsWith("+")) {
            return new CodeDiffResponse.DiffLine("add", null, newLine, rawLine.substring(1).strip());
        }
        if (rawLine.startsWith("-")) {
            return new CodeDiffResponse.DiffLine("delete", oldLine, null, rawLine.substring(1).strip());
        }
        if (rawLine.startsWith(" ")) {
            return new CodeDiffResponse.DiffLine("context", oldLine, newLine, rawLine.substring(1).strip());
        }
        return null;
    }

    private CodeSemanticSearchResponse.SemanticMatch scoreChunk(
            CodeChunkResponse.CodeChunk chunk,
            List<String> terms
    ) {
        String searchable = (chunk.path() + "\n" + chunk.content()).toLowerCase(Locale.ROOT);
        List<String> matchedTerms = terms.stream()
                .filter(searchable::contains)
                .toList();
        int score = matchedTerms.stream()
                .mapToInt(term -> scoreTerm(searchable, term))
                .sum();
        return new CodeSemanticSearchResponse.SemanticMatch(
                chunk.path(),
                chunk.startLine(),
                chunk.endLine(),
                score,
                matchedTerms,
                chunk.content()
        );
    }

    private int scoreTerm(String searchable, String term) {
        int score = Math.min(term.length(), 12);
        int occurrences = 0;
        int index = searchable.indexOf(term);
        while (index >= 0) {
            occurrences++;
            index = searchable.indexOf(term, index + term.length());
        }
        return score * Math.max(1, occurrences);
    }

    private List<String> extractTerms(String query) {
        Matcher matcher = TERM_PATTERN.matcher(query);
        List<String> terms = new ArrayList<>();
        while (matcher.find()) {
            String rawTerm = matcher.group();
            addTerm(terms, rawTerm.toLowerCase(Locale.ROOT));
            for (String part : splitCamelCase(rawTerm)) {
                addTerm(terms, part);
            }
        }
        return terms;
    }

    private List<String> splitCamelCase(String term) {
        String spaced = term.replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .replace('-', ' ');
        return Stream.of(spaced.split("\\s+"))
                .map(part -> part.toLowerCase(Locale.ROOT))
                .filter(part -> part.length() >= 2)
                .toList();
    }

    private void addTerm(List<String> terms, String term) {
        if (term.length() >= 2 && !terms.contains(term)) {
            terms.add(term);
        }
    }

    private int totalLines(Map<String, List<CodeDiffResponse.DiffLine>> linesByPath) {
        return linesByPath.values().stream().mapToInt(List::size).sum();
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new CodeContextException(
                    "CODE_SEARCH_QUERY_REQUIRED",
                    HttpStatus.BAD_REQUEST,
                    "query is required"
            );
        }
        return query.strip();
    }

    private int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private String extension(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizePath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
