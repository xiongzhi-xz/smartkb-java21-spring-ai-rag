package com.smartkb.agent.application;

import com.smartkb.agent.domain.CodeContextException;
import com.smartkb.agent.domain.CodeDiffRequest;
import com.smartkb.agent.domain.CodeDiffResponse;
import com.smartkb.agent.domain.CodeSearchRequest;
import com.smartkb.agent.domain.CodeSearchResponse;
import com.smartkb.agent.domain.CodeTreeRequest;
import com.smartkb.agent.domain.CodeTreeResponse;
import com.smartkb.agent.infrastructure.filesystem.ProjectPathGuard;
import com.smartkb.agent.infrastructure.git.GitDiffReader;
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

    @Test
    void shouldReturnGitDiffEvidenceAndSkipSensitiveFiles() throws IOException, InterruptedException {
        runGit("init");
        write("src/main/java/com/example/App.java", """
                class App {
                    void oldMethod() {}
                }
                """);
        write(".env", "TOKEN=secret\n");
        runGit("add", "src/main/java/com/example/App.java", ".env");
        write("src/main/java/com/example/App.java", """
                class App {
                    void newMethod() {}
                    void reserveTicket() {}
                }
                """);

        CodeDiffResponse response = service().diff(new CodeDiffRequest(
                tempDir.toString(),
                "reserveTicket",
                20
        ));

        assertTrue(response.success());
        assertTrue(response.gitRepository());
        assertEquals("reserveTicket", response.query());
        assertTrue(response.files().stream()
                .anyMatch(file -> file.path().equals("src/main/java/com/example/App.java")
                        && file.lines().stream().anyMatch(line -> "add".equals(line.type())
                        && line.newLineNumber() == 3
                        && line.content().contains("reserveTicket"))));
        assertFalse(response.files().stream().anyMatch(file -> ".env".equals(file.path())));
        assertTrue(response.skippedFiles().stream().anyMatch(file -> ".env".equals(file.path())));
    }

    private CodeContextService service() {
        return new CodeContextService(new ProjectPathGuard(), new GitDiffReader());
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private void runGit(String... arguments) throws IOException, InterruptedException {
        String[] command = new String[arguments.length + 1];
        command[0] = "git";
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        Process process = new ProcessBuilder(command)
                .directory(tempDir.toFile())
                .redirectErrorStream(true)
                .start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            fail("git command failed: " + String.join(" ", command));
        }
    }
}
