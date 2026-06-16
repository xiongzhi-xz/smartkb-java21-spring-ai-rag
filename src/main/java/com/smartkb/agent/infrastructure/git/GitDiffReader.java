package com.smartkb.agent.infrastructure.git;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs read-only Git diff commands.
 */
@Component
public class GitDiffReader {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    public boolean isGitRepository(Path root) {
        CommandResult result = run(root, "git", "rev-parse", "--is-inside-work-tree");
        return result.exitCode() == 0 && result.output().trim().equals("true");
    }

    public List<String> changedFiles(Path root, List<String> warnings) {
        List<String> files = new ArrayList<>();
        CommandResult unstaged = run(root, "git", "diff", "--name-only");
        if (unstaged.exitCode() == 0) {
            files.addAll(lines(unstaged.output()));
        } else {
            warnings.add("git diff --name-only failed: " + unstaged.output().trim());
        }

        CommandResult staged = run(root, "git", "diff", "--cached", "--name-only");
        if (staged.exitCode() == 0) {
            for (String file : lines(staged.output())) {
                if (!files.contains(file)) {
                    files.add(file);
                }
            }
        } else {
            warnings.add("git diff --cached --name-only failed: " + staged.output().trim());
        }
        return files;
    }

    public String diff(Path root, String relativePath, boolean staged) {
        CommandResult result = staged
                ? run(root, "git", "diff", "--cached", "--unified=3", "--", relativePath)
                : run(root, "git", "diff", "--unified=3", "--", relativePath);
        if (result.exitCode() != 0) {
            return "";
        }
        return result.output();
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
                .filter(line -> !line.startsWith("warning:"))
                .filter(line -> !line.isBlank())
                .toList();
    }

    private record CommandResult(int exitCode, String output) {
    }
}
