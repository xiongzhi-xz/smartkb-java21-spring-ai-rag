package com.smartkb.agent.application;

import com.smartkb.agent.domain.CreateMemoryRecordRequest;
import com.smartkb.agent.domain.MemoryAuthorityLevel;
import com.smartkb.agent.domain.MemoryRecordException;
import com.smartkb.agent.domain.MemoryRecordResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryRecordServiceTest {

    private final MemoryRecordService service = new MemoryRecordService();

    @Test
    void shouldCreateMemoryWithAuthorityAndTags() {
        MemoryRecordResponse memory = service.create(new CreateMemoryRecordRequest(
                "ticket-project",
                MemoryAuthorityLevel.HIGH,
                "SPEC",
                "SPEC.md",
                "Do not expand TicketRush scope before k6 benchmark data exists.",
                List.of("scope", "benchmark", "scope", " ")
        ));

        assertEquals("ticket-project", memory.projectId());
        assertEquals(MemoryAuthorityLevel.HIGH, memory.authorityLevel());
        assertEquals("SPEC", memory.sourceType());
        assertEquals("SPEC.md", memory.sourcePath());
        assertEquals(List.of("scope", "benchmark"), memory.tags());
        assertEquals(memory.id(), service.get(memory.id()).id());
    }

    @Test
    void shouldDefaultToLowAuthorityAndManualSource() {
        MemoryRecordResponse memory = service.create(new CreateMemoryRecordRequest(
                null,
                null,
                null,
                null,
                "Temporary observation",
                null
        ));

        assertEquals(MemoryAuthorityLevel.LOW, memory.authorityLevel());
        assertEquals("manual", memory.sourceType());
        assertTrue(memory.tags().isEmpty());
    }

    @Test
    void shouldFilterMemoriesByProjectAuthorityAndSource() {
        service.create(new CreateMemoryRecordRequest(
                "ticket-project",
                MemoryAuthorityLevel.HIGH,
                "SPEC",
                "SPEC.md",
                "Ticket project high memory",
                List.of("ticket")
        ));
        service.create(new CreateMemoryRecordRequest(
                "ticket-project",
                MemoryAuthorityLevel.MEDIUM,
                "task",
                null,
                "Ticket task memory",
                List.of("ticket")
        ));
        service.create(new CreateMemoryRecordRequest(
                "smartkb",
                MemoryAuthorityLevel.HIGH,
                "SPEC",
                "SPEC.md",
                "SmartKB high memory",
                List.of("smartkb")
        ));

        List<MemoryRecordResponse> filtered = service.list("ticket-project", MemoryAuthorityLevel.HIGH, "SPEC");

        assertEquals(1, filtered.size());
        assertEquals("Ticket project high memory", filtered.getFirst().content());
    }

    @Test
    void shouldRejectBlankContent() {
        MemoryRecordException exception = assertThrows(
                MemoryRecordException.class,
                () -> service.create(new CreateMemoryRecordRequest(
                        null,
                        MemoryAuthorityLevel.HIGH,
                        "SPEC",
                        "SPEC.md",
                        " ",
                        null
                ))
        );

        assertEquals("MEMORY_CONTENT_REQUIRED", exception.code());
    }

    @Test
    void shouldRejectMissingMemory() {
        MemoryRecordException exception = assertThrows(
                MemoryRecordException.class,
                () -> service.get("missing")
        );

        assertEquals("MEMORY_NOT_FOUND", exception.code());
    }
}
