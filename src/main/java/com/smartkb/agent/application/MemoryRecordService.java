package com.smartkb.agent.application;

import com.smartkb.agent.domain.CreateMemoryRecordRequest;
import com.smartkb.agent.domain.MemoryAuthorityLevel;
import com.smartkb.agent.domain.MemoryRecordException;
import com.smartkb.agent.domain.MemoryRecordResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class MemoryRecordService {

    private final ConcurrentMap<String, MemoryRecordResponse> memories = new ConcurrentHashMap<>();

    public MemoryRecordResponse create(CreateMemoryRecordRequest request) {
        String content = requireText(request == null ? null : request.content(), "MEMORY_CONTENT_REQUIRED", "content is required");
        String sourceType = normalizeOrDefault(request == null ? null : request.sourceType(), "manual");
        String now = now();
        MemoryRecordResponse memory = new MemoryRecordResponse(
                UUID.randomUUID().toString(),
                normalize(request == null ? null : request.projectId()),
                request == null || request.authorityLevel() == null ? MemoryAuthorityLevel.LOW : request.authorityLevel(),
                sourceType,
                normalize(request == null ? null : request.sourcePath()),
                content,
                normalizeTags(request == null ? null : request.tags()),
                now,
                now
        );
        memories.put(memory.id(), memory);
        return memory;
    }

    public MemoryRecordResponse get(String id) {
        String memoryId = requireText(id, "MEMORY_ID_REQUIRED", "id is required");
        MemoryRecordResponse memory = memories.get(memoryId);
        if (memory == null) {
            throw new MemoryRecordException("MEMORY_NOT_FOUND", HttpStatus.NOT_FOUND, "memory not found");
        }
        return memory;
    }

    public List<MemoryRecordResponse> list(String projectId, MemoryAuthorityLevel authorityLevel, String sourceType) {
        String normalizedProjectId = normalize(projectId);
        String normalizedSourceType = normalize(sourceType);
        return memories.values().stream()
                .filter(memory -> normalizedProjectId == null || normalizedProjectId.equals(memory.projectId()))
                .filter(memory -> authorityLevel == null || authorityLevel == memory.authorityLevel())
                .filter(memory -> normalizedSourceType == null || normalizedSourceType.equals(memory.sourceType()))
                .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
                .toList();
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .map(this::normalize)
                .filter(tag -> tag != null)
                .distinct()
                .toList();
    }

    private String requireText(String value, String code, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new MemoryRecordException(code, HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeOrDefault(String value, String fallback) {
        String normalized = normalize(value);
        return normalized == null ? fallback : normalized;
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String now() {
        return OffsetDateTime.now().toString();
    }
}
