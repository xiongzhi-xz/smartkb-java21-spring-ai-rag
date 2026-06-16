package com.smartkb.agent.domain;

import java.util.List;

public record CreateMemoryRecordRequest(
        String projectId,
        MemoryAuthorityLevel authorityLevel,
        String sourceType,
        String sourcePath,
        String content,
        List<String> tags
) {
}
