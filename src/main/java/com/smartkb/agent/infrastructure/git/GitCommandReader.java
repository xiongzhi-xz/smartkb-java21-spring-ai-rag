package com.smartkb.agent.infrastructure.git;

import com.smartkb.agent.domain.ProjectRawContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs read-only Git commands for project intake.
 */
@Component
public class GitCommandReader {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public ProjectRawContext.GitInfo read(Path root) {
        List<String> warnings = new ArrayList<>();
        CommandResult status = run(root, "git", "status", "--short");
        if (status.exitCode() != 0) {
            warnings.add("git status --short 执行失败: " + status.output());
            return new ProjectRawContext.GitInfo(false, "", List.of(), warnings);
        }

        CommandResult log = run(root, "git", "log", "--oneline", "-5");
        List<String> commits = log.exitCode() == 0
                ? lines(log.output())
                : List.of();
        if (log.exitCode() != 0) {
            warnings.add("git log --oneline -5 执行失败: " + log.output());
        }

        return new ProjectRawContext.GitInfo(true, status.output().trim(), commits, warnings);
    }

    private CommandResult run(Path root, String... command) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(root.toFile());
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            boolean completed = process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                return new CommandResult(124, "command timeout");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CommandResult(process.exitValue(), output);
        } catch (IOException exception) {
            return new CommandResult(1, exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new CommandResult(130, "command interrupted");
        }
    }

    private List<String> lines(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }
        return output.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private record CommandResult(int exitCode, String output) {
    }
}
