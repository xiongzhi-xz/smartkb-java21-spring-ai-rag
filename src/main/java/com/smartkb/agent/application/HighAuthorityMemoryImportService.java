package com.smartkb.agent.application;

import com.smartkb.agent.domain.CreateMemoryRecordRequest;
import com.smartkb.agent.domain.ImportHighAuthorityMemoryRequest;
import com.smartkb.agent.domain.ImportHighAuthorityMemoryResponse;
import com.smartkb.agent.domain.MemoryAuthorityLevel;
import com.smartkb.agent.domain.MemoryRecordResponse;
import com.smartkb.agent.infrastructure.filesystem.ProjectPathGuard;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class HighAuthorityMemoryImportService {

    private static final int DEFAULT_MAX_FILE_BYTES = 65_536;
    private static final int MAX_MEMORY_CHARS = 4_000;
    private static final List<String> HIGH_AUTHORITY_FILES = List.of("SPEC.md", "HANDOFF.md");

    private final MemoryRecordService memoryRecordService;
    private final ProjectPathGuard pathGuard;

    public HighAuthorityMemoryImportService(MemoryRecordService memoryRecordService, ProjectPathGuard pathGuard) {
        this.memoryRecordService = memoryRecordService;
        this.pathGuard = pathGuard;
    }

    public ImportHighAuthorityMemoryResponse importFromProjectDocs(ImportHighAuthorityMemoryRequest request) {
        Path root = pathGuard.validateProjectRoot(request == null ? null : request.rootPath());
        int maxFileBytes = normalizeMaxFileBytes(request == null ? null : request.maxFileBytes());
        String projectId = request == null ? null : request.projectId();
        List<MemoryRecordResponse> imported = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (String fileName : HIGH_AUTHORITY_FILES) {
            Path file = root.resolve(fileName).normalize();
            if (!Files.isRegularFile(file)) {
                skipped.add(fileName + ": missing");
                continue;
            }
            if (pathGuard.shouldSkip(root, file)) {
                skipped.add(fileName + ": " + pathGuard.skipReason(root, file));
                continue;
            }
            try {
                long size = Files.size(file);
                if (size > maxFileBytes) {
                    skipped.add(fileName + ": too large");
                    continue;
                }
                String content = Files.readString(file, StandardCharsets.UTF_8);
                imported.add(memoryRecordService.create(new CreateMemoryRecordRequest(
                        projectId,
                        MemoryAuthorityLevel.HIGH,
                        sourceType(fileName),
                        fileName,
                        toMemoryContent(fileName, content),
                        List.of("high-authority", "project-doc", sourceType(fileName).toLowerCase())
                )));
            } catch (IOException e) {
                skipped.add(fileName + ": read failed");
            }
        }

        return new ImportHighAuthorityMemoryResponse(List.copyOf(imported), List.copyOf(skipped));
    }

    private int normalizeMaxFileBytes(Integer value) {
        if (value == null || value <= 0) {
            return DEFAULT_MAX_FILE_BYTES;
        }
        return Math.max(1_024, Math.min(value, DEFAULT_MAX_FILE_BYTES));
    }

    private String sourceType(String fileName) {
        return fileName.replace(".md", "").toUpperCase();
    }

    private String toMemoryContent(String fileName, String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.length() > MAX_MEMORY_CHARS) {
            normalized = normalized.substring(0, MAX_MEMORY_CHARS) + "\n...";
        }
        return "High authority memory from " + fileName + ":\n" + normalized;
    }
}
