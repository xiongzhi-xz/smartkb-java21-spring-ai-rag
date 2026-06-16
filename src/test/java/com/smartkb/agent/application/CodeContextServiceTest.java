package com.smartkb.agent.application;

import com.smartkb.agent.domain.CodeContextException;
import com.smartkb.agent.domain.CodeSearchRequest;
import com.smartkb.agent.domain.CodeSearchResponse;
import com.smartkb.agent.domain.CodeTreeRequest;
import com.smartkb.agent.domain.CodeTreeResponse;
import com.smartkb.agent.infrastructure.filesystem.ProjectPathGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CodeContextServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReturnSafeFileTree() throws IOException {
        write("src/main/java/com/example/App.java", "class App {}\n");
        write("docs/guide.md", "# Guide\n");
        write(".env", "TOKEN=secret\n");
        write("target/generated.txt", "generated\n");

        CodeTreeResponse response = service().tree(new CodeTreeRequest(tempDir.toString(), 20, 6));

        assertTrue(response.success());
        assertTrue(response.files().stream().anyMatch(file -> "src/main/java/com/example/App.java".equals(file.path())));
        assertTrue(response.files().stream().anyMatch(file -> "docs/guide.md".equals(file.path())));
        assertFalse(response.files().stream().anyMatch(file -> ".env".equals(file.path())));
        assertFalse(response.files().stream().anyMatch(file -> file.path().startsWith("target/")));
        assertTrue(response.skippedFiles().stream().anyMatch(file -> ".env".equals(file.path())));
        assertTrue(response.skippedFiles().stream().anyMatch(file -> "target".equals(file.path())));
    }

    @Test
    void shouldSearchKeywordWithLineNumbers() throws IOException {
        write("src/main/java/com/example/TicketService.java", """
                package com.example;

                class TicketService {
                    void reserveTicket() {}
                }
                """);
        write("src/test/java/com/example/TicketServiceTest.java", "class TicketServiceTest {}\n");
        write(".env", "TicketService=secret\n");

        CodeSearchResponse response = service().search(new CodeSearchRequest(
                tempDir.toString(),
                "TicketService",
                10,
                65_536
        ));

        assertTrue(response.success());
        assertEquals("TicketService", response.query());
        assertTrue(response.matches().stream()
                .anyMatch(match -> match.path().equals("src/main/java/com/example/TicketService.java")
                        && match.lineNumber() == 3
                        && match.line().contains("class TicketService")));
        assertTrue(response.matches().stream()
                .anyMatch(match -> match.path().equals("src/test/java/com/example/TicketServiceTest.java")
                        && match.lineNumber() == 1));
        assertFalse(response.matches().stream().anyMatch(match -> ".env".equals(match.path())));
        assertTrue(response.skippedFiles().stream().anyMatch(file -> ".env".equals(file.path())));
    }

    @Test
    void shouldRejectBlankSearchQuery() {
        CodeContextException exception = assertThrows(
                CodeContextException.class,
                () -> service().search(new CodeSearchRequest(tempDir.toString(), " ", null, null))
        );

        assertEquals("CODE_SEARCH_QUERY_REQUIRED", exception.code());
    }

    private CodeContextService service() {
        return new CodeContextService(new ProjectPathGuard());
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
