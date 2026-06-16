package com.smartkb.agent.application;

import com.smartkb.agent.domain.MemoryAuthorityLevel;
import com.smartkb.agent.domain.MemoryConflictCheckRequest;
import com.smartkb.agent.domain.MemoryConflictCheckResponse;
import com.smartkb.agent.domain.MemoryRecordException;
import com.smartkb.agent.domain.MemoryRecordResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MemoryConflictService {

    private final MemoryRecordService memoryRecordService;

    public MemoryConflictService(MemoryRecordService memoryRecordService) {
        this.memoryRecordService = memoryRecordService;
    }

    public MemoryConflictCheckResponse check(MemoryConflictCheckRequest request) {
        String projectId = requireText(request == null ? null : request.projectId(), "MEMORY_PROJECT_REQUIRED", "projectId is required");
        MemoryAuthorityLevel incomingLevel = request == null || request.authorityLevel() == null
                ? MemoryAuthorityLevel.LOW
                : request.authorityLevel();
        Set<String> incomingTags = normalizeTags(request == null ? null : request.tags());
        if (incomingTags.isEmpty()) {
            throw new MemoryRecordException("MEMORY_TAGS_REQUIRED", HttpStatus.BAD_REQUEST, "tags are required");
        }

        List<MemoryRecordResponse> conflicts = memoryRecordService.list(projectId, null, null).stream()
                .filter(memory -> rank(memory.authorityLevel()) > rank(incomingLevel))
                .filter(memory -> hasTagOverlap(memory, incomingTags))
                .sorted(Comparator.comparingInt((MemoryRecordResponse memory) -> rank(memory.authorityLevel()))
                        .reversed()
                        .thenComparing(MemoryRecordResponse::createdAt, Comparator.reverseOrder()))
                .toList();

        if (conflicts.isEmpty()) {
            return new MemoryConflictCheckResponse(
                    false,
                    null,
                    List.of(),
                    "No higher-authority memory conflicts were found."
            );
        }

        MemoryRecordResponse preferred = conflicts.getFirst();
        return new MemoryConflictCheckResponse(
                true,
                preferred,
                conflicts,
                "Use " + preferred.authorityLevel() + " memory from "
                        + preferred.sourceType() + " " + preferred.sourcePath()
                        + " before accepting lower-authority input."
        );
    }

    private boolean hasTagOverlap(MemoryRecordResponse memory, Set<String> incomingTags) {
        return memory.tags().stream()
                .map(this::normalize)
                .anyMatch(incomingTags::contains);
    }

    private Set<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return Set.of();
        }
        return tags.stream()
                .map(this::normalize)
                .filter(tag -> tag != null)
                .collect(Collectors.toSet());
    }

    private String requireText(String value, String code, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new MemoryRecordException(code, HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private int rank(MemoryAuthorityLevel level) {
        return switch (level) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        };
    }
}
