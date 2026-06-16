package com.smartkb.agent.domain;

import java.util.List;

public record MemoryConflictCheckResponse(
        boolean hasConflict,
        MemoryRecordResponse preferredMemory,
        List<MemoryRecordResponse> conflictingMemories,
        String recommendation
) {
}
