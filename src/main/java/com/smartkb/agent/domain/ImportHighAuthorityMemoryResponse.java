package com.smartkb.agent.domain;

import java.util.List;

public record ImportHighAuthorityMemoryResponse(
        List<MemoryRecordResponse> imported,
        List<String> skippedFiles
) {
}
