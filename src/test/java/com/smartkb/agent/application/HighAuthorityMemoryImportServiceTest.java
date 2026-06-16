package com.smartkb.agent.application;

import com.smartkb.agent.domain.ImportHighAuthorityMemoryRequest;
import com.smartkb.agent.domain.ImportHighAuthorityMemoryResponse;
import com.smartkb.agent.domain.MemoryAuthorityLevel;
import com.smartkb.agent.infrastructure.filesystem.ProjectPathGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighAuthorityMemoryImportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldImportSpecAndHandoffAsHighAuthorityMemories() throws IOException {
        Files.writeString(tempDir.resolve("SPEC.md"), "# SPEC\n\nDo not expand scope before benchmark data.");
        Files.writeString(tempDir.resolve("HANDOFF.md"), "# HANDOFF\n\nNext step is k6 benchmark.");

        ImportHighAuthorityMemoryResponse response = service().importFromProjectDocs(
                new ImportHighAuthorityMemoryRequest("ticket-project", tempDir.toString(), 65_536)
        );

        assertEquals(2, response.imported().size());
        assertTrue(response.skippedFiles().isEmpty());
        assertTrue(response.imported().stream().allMatch(memory -> memory.authorityLevel() == MemoryAuthorityLevel.HIGH));
        assertTrue(response.imported().stream().anyMatch(memory ->
                "SPEC".equals(memory.sourceType())
                        && "SPEC.md".equals(memory.sourcePath())
                        && memory.content().contains("Do not expand scope")
        ));
        assertTrue(response.imported().stream().anyMatch(memory ->
                "HANDOFF".equals(memory.sourceType())
                        && "HANDOFF.md".equals(memory.sourcePath())
                        && memory.content().contains("Next step is k6")
        ));
    }

    @Test
    void shouldSkipMissingHighAuthorityFiles() {
        ImportHighAuthorityMemoryResponse response = service().importFromProjectDocs(
                new ImportHighAuthorityMemoryRequest("ticket-project", tempDir.toString(), 65_536)
        );

        assertTrue(response.imported().isEmpty());
        assertEquals(2, response.skippedFiles().size());
        assertTrue(response.skippedFiles().contains("SPEC.md: missing"));
        assertTrue(response.skippedFiles().contains("HANDOFF.md: missing"));
    }

    @Test
    void shouldSkipFileLargerThanLimit() throws IOException {
        Files.writeString(tempDir.resolve("SPEC.md"), "x".repeat(2_000));

        ImportHighAuthorityMemoryResponse response = service().importFromProjectDocs(
                new ImportHighAuthorityMemoryRequest("ticket-project", tempDir.toString(), 1_024)
        );

        assertTrue(response.imported().isEmpty());
        assertTrue(response.skippedFiles().contains("SPEC.md: too large"));
    }

    private HighAuthorityMemoryImportService service() {
        return new HighAuthorityMemoryImportService(new MemoryRecordService(), new ProjectPathGuard());
    }
}
