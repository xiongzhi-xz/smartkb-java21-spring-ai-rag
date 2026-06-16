package com.smartkb.agent.domain;

import java.util.List;

public record MemoryRecordResponse(
        String id,
        String projectId,
        MemoryAuthorityLevel authorityLevel,
        String sourceType,
        String sourcePath,
        String content,
        List<String> tags,
        String createdAt,
        String updatedAt
) {
}
