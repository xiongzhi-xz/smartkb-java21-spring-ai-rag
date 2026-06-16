package com.smartkb.agent.infrastructure.filesystem;

import com.smartkb.agent.domain.ProjectIntakeResponse;
import com.smartkb.agent.domain.ProjectRawContext;
import com.smartkb.agent.infrastructure.git.GitCommandReader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads whitelisted project files and a small file tree snapshot.
 */
@Component
public class ProjectFileReader {

    private static final List<String> ROOT_FILE_ALLOWLIST = List.of(
            "README.md",
            "SPEC.md",
            "AGENTS.md",
            "CLAUDE.md",
            "HANDOFF.md",
            "PROJECT.md",
            "PROJECT_STATUS.md",
            "TODO.md",
            "CHANGELOG.md",
            "pom.xml",
            "build.gradle",
            "settings.gradle",
            "package.json",
            "docker-compose.yml",
            "Dockerfile"
    );

    private static final List<String> TREE_ROOTS = List.of("src/main", "src/test", "docs");

    private final ProjectPathGuard pathGuard;
    private final GitCommandReader gitCommandReader;

    public ProjectFileReader(ProjectPathGuard pathGuard, GitCommandReader gitCommandReader) {
        this.pathGuard = pathGuard;
        this.gitCommandReader = gitCommandReader;
    }

    public ProjectRawContext read(Path root, boolean includeCodeTree, int maxFiles, int maxFileBytes) {
        Map<String, String> contents = new LinkedHashMap<>();
        List<String> fileTree = new ArrayList<>();
        List<ProjectIntakeResponse.SkippedFile> skipped = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (String fileName : ROOT_FILE_ALLOWLIST) {
            Path file = root.resolve(fileName).normalize();
            if (!Files.isRegularFile(file)) {
                continue;
            }
            readFile(root, file, maxFileBytes, contents, skipped, warnings);
        }

        if (includeCodeTree) {
            readFileTree(root, maxFiles, fileTree, skipped, warnings);
        }

        ProjectRawContext.GitInfo gitInfo = gitCommandReader.read(root);
        warnings.addAll(gitInfo.warnings());
        return new ProjectRawContext(root, contents, fileTree, skipped, warnings, gitInfo);
    }

    private void readFile(
            Path root,
            Path file,
            int maxFileBytes,
            Map<String, String> contents,
            List<ProjectIntakeResponse.SkippedFile> skipped,
            List<String> warnings
    ) {
        String relative = pathGuard.toRelative(root, file);
        if (pathGuard.shouldSkip(root, file)) {
            skipped.add(new ProjectIntakeResponse.SkippedFile(relative, pathGuard.skipReason(root, file)));
            return;
        }
        try {
            long size = Files.size(file);
            if (size > maxFileBytes) {
                skipped.add(new ProjectIntakeResponse.SkippedFile(relative, "file too large"));
                return;
            }
            contents.put(relative, Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            warnings.add("读取文件失败: " + relative + " (" + exception.getMessage() + ")");
        }
    }

    private void readFileTree(
            Path root,
            int maxFiles,
            List<String> fileTree,
            List<ProjectIntakeResponse.SkippedFile> skipped,
            List<String> warnings
    ) {
        for (String treeRoot : TREE_ROOTS) {
            Path start = root.resolve(treeRoot).normalize();
            if (!Files.exists(start)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(start, 8)) {
                paths.filter(Files::isRegularFile)
                        .forEach(path -> {
                            String relative = pathGuard.toRelative(root, path);
                            if (fileTree.size() >= maxFiles) {
                                return;
                            }
                            if (pathGuard.shouldSkip(root, path)) {
                                skipped.add(new ProjectIntakeResponse.SkippedFile(
                                        relative,
                                        pathGuard.skipReason(root, path)
                                ));
                                return;
                            }
                            fileTree.add(relative);
                        });
            } catch (IOException exception) {
                warnings.add("读取文件树失败: " + treeRoot + " (" + exception.getMessage() + ")");
            }
        }
    }
}
