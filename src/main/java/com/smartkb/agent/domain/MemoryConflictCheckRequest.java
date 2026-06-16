package com.smartkb.agent.domain;

import java.util.List;

public record MemoryConflictCheckRequest(
        String projectId,
        MemoryAuthorityLevel authorityLevel,
        String content,
        List<String> tags
) {
}
