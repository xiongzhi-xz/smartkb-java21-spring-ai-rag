package com.smartkb.agent.application;

import com.smartkb.agent.domain.CreateMemoryRecordRequest;
import com.smartkb.agent.domain.MemoryAuthorityLevel;
import com.smartkb.agent.domain.MemoryConflictCheckRequest;
import com.smartkb.agent.domain.MemoryConflictCheckResponse;
import com.smartkb.agent.domain.MemoryRecordException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryConflictServiceTest {

    private final MemoryRecordService memoryRecordService = new MemoryRecordService();
    private final MemoryConflictService service = new MemoryConflictService(memoryRecordService);

    @Test
    void shouldPreferHigherAuthorityMemoryWithOverlappingTags() {
        memoryRecordService.create(new CreateMemoryRecordRequest(
                "ticket-project",
                MemoryAuthorityLevel.HIGH,
                "SPEC",
                "SPEC.md",
                "Do not expand scope before benchmark data.",
                List.of("scope", "benchmark")
        ));

        MemoryConflictCheckResponse response = service.check(new MemoryConflictCheckRequest(
                "ticket-project",
                MemoryAuthorityLevel.LOW,
                "Maybe add a new feature now.",
                List.of("scope")
        ));

        assertTrue(response.hasConflict());
        assertEquals(MemoryAuthorityLevel.HIGH, response.preferredMemory().authorityLevel());
        assertEquals("SPEC.md", response.preferredMemory().sourcePath());
        assertTrue(response.recommendation().contains("HIGH memory"));
    }

    @Test
    void shouldIgnoreEqualOrLowerAuthorityMemories() {
        memoryRecordService.create(new CreateMemoryRecordRequest(
                "ticket-project",
                MemoryAuthorityLevel.LOW,
                "chat",
                null,
                "Temporary low authority note.",
                List.of("scope")
        ));

        MemoryConflictCheckResponse response = service.check(new MemoryConflictCheckRequest(
                "ticket-project",
                MemoryAuthorityLevel.HIGH,
                "High authority input",
                List.of("scope")
        ));

        assertFalse(response.hasConflict());
        assertTrue(response.conflictingMemories().isEmpty());
    }

    @Test
    void shouldRequireTagsForConflictCheck() {
        MemoryRecordException exception = assertThrows(
                MemoryRecordException.class,
                () -> service.check(new MemoryConflictCheckRequest(
                        "ticket-project",
                        MemoryAuthorityLevel.LOW,
                        "No tags",
                        List.of()
                ))
        );

        assertEquals("MEMORY_TAGS_REQUIRED", exception.code());
    }
}
