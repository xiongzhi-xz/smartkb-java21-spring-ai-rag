package com.smartkb.agent.controller;

import com.smartkb.agent.application.HighAuthorityMemoryImportService;
import com.smartkb.agent.application.MemoryConflictService;
import com.smartkb.agent.application.MemoryRecordService;
import com.smartkb.agent.domain.CreateMemoryRecordRequest;
import com.smartkb.agent.domain.ImportHighAuthorityMemoryRequest;
import com.smartkb.agent.domain.ImportHighAuthorityMemoryResponse;
import com.smartkb.agent.domain.MemoryAuthorityLevel;
import com.smartkb.agent.domain.MemoryConflictCheckRequest;
import com.smartkb.agent.domain.MemoryConflictCheckResponse;
import com.smartkb.agent.domain.MemoryRecordException;
import com.smartkb.agent.domain.MemoryRecordResponse;
import com.smartkb.config.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemoryRecordController.class)
@Import(GlobalExceptionHandler.class)
class MemoryRecordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MemoryRecordService memoryRecordService;

    @MockBean
    private HighAuthorityMemoryImportService highAuthorityMemoryImportService;

    @MockBean
    private MemoryConflictService memoryConflictService;

    @Test
    void shouldCreateMemory() throws Exception {
        when(memoryRecordService.create(any(CreateMemoryRecordRequest.class))).thenReturn(memory());

        mockMvc.perform(post("/api/agent/memories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "ticket-project",
                                  "authorityLevel": "HIGH",
                                  "sourceType": "SPEC",
                                  "sourcePath": "SPEC.md",
                                  "content": "Do not expand scope before benchmark data.",
                                  "tags": ["scope", "benchmark"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("memory-1"))
                .andExpect(jsonPath("$.authorityLevel").value("HIGH"))
                .andExpect(jsonPath("$.tags[0]").value("scope"));

        ArgumentCaptor<CreateMemoryRecordRequest> captor = ArgumentCaptor.forClass(CreateMemoryRecordRequest.class);
        verify(memoryRecordService).create(captor.capture());
        assertEquals("ticket-project", captor.getValue().projectId());
        assertEquals(MemoryAuthorityLevel.HIGH, captor.getValue().authorityLevel());
    }

    @Test
    void shouldListMemoriesWithFilters() throws Exception {
        when(memoryRecordService.list("ticket-project", MemoryAuthorityLevel.HIGH, "SPEC"))
                .thenReturn(List.of(memory()));

        mockMvc.perform(get("/api/agent/memories")
                        .param("projectId", "ticket-project")
                        .param("authorityLevel", "HIGH")
                        .param("sourceType", "SPEC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("memory-1"));

        verify(memoryRecordService).list("ticket-project", MemoryAuthorityLevel.HIGH, "SPEC");
    }

    @Test
    void shouldGetMemory() throws Exception {
        when(memoryRecordService.get("memory-1")).thenReturn(memory());

        mockMvc.perform(get("/api/agent/memories/memory-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Do not expand scope before benchmark data."));
    }

    @Test
    void shouldReturnMemoryErrorResponse() throws Exception {
        when(memoryRecordService.get(eq("missing"))).thenThrow(
                new MemoryRecordException("MEMORY_NOT_FOUND", HttpStatus.NOT_FOUND, "memory not found")
        );

        mockMvc.perform(get("/api/agent/memories/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MEMORY_NOT_FOUND"))
                .andExpect(jsonPath("$.error").value("memory not found"));
    }

    @Test
    void shouldImportHighAuthorityMemories() throws Exception {
        when(highAuthorityMemoryImportService.importFromProjectDocs(any(ImportHighAuthorityMemoryRequest.class)))
                .thenReturn(new ImportHighAuthorityMemoryResponse(List.of(memory()), List.of("HANDOFF.md: missing")));

        mockMvc.perform(post("/api/agent/memories/import/high-authority")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "ticket-project",
                                  "rootPath": "E:/project/work/job/ticketrush-java21-high-concurrency",
                                  "maxFileBytes": 65536
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported[0].id").value("memory-1"))
                .andExpect(jsonPath("$.skippedFiles[0]").value("HANDOFF.md: missing"));

        ArgumentCaptor<ImportHighAuthorityMemoryRequest> captor =
                ArgumentCaptor.forClass(ImportHighAuthorityMemoryRequest.class);
        verify(highAuthorityMemoryImportService).importFromProjectDocs(captor.capture());
        assertEquals("ticket-project", captor.getValue().projectId());
    }

    @Test
    void shouldCheckMemoryConflicts() throws Exception {
        when(memoryConflictService.check(any(MemoryConflictCheckRequest.class)))
                .thenReturn(new MemoryConflictCheckResponse(
                        true,
                        memory(),
                        List.of(memory()),
                        "Use HIGH memory from SPEC SPEC.md before accepting lower-authority input."
                ));

        mockMvc.perform(post("/api/agent/memories/conflicts/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "ticket-project",
                                  "authorityLevel": "LOW",
                                  "content": "Maybe expand scope now.",
                                  "tags": ["scope"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasConflict").value(true))
                .andExpect(jsonPath("$.preferredMemory.sourcePath").value("SPEC.md"))
                .andExpect(jsonPath("$.recommendation").value("Use HIGH memory from SPEC SPEC.md before accepting lower-authority input."));

        ArgumentCaptor<MemoryConflictCheckRequest> captor =
                ArgumentCaptor.forClass(MemoryConflictCheckRequest.class);
        verify(memoryConflictService).check(captor.capture());
        assertEquals(MemoryAuthorityLevel.LOW, captor.getValue().authorityLevel());
    }

    private MemoryRecordResponse memory() {
        return new MemoryRecordResponse(
                "memory-1",
                "ticket-project",
                MemoryAuthorityLevel.HIGH,
                "SPEC",
                "SPEC.md",
                "Do not expand scope before benchmark data.",
                List.of("scope", "benchmark"),
                "2026-06-17T04:00:00+08:00",
                "2026-06-17T04:00:00+08:00"
        );
    }
}
