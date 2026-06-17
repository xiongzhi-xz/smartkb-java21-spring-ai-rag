package com.smartkb.agent.domain;

import java.util.List;

/**
 * Structured Project Intake response.
 */
public record ProjectIntakeResponse(
        boolean success,
        ProjectSummary project,
        IntakeSummary intake,
        List<ProjectEvidence> evidence,
        ProjectReadLog readLog,
        List<String> warnings
) {

    public record ProjectSummary(
            String id,
            String name,
            String rootPath,
            List<String> detectedStack,
            String buildTool,
            List<String> packageManagerEvidence,
            List<String> testCommands,
            String createdAt
    ) {
    }

    public record IntakeSummary(
            String currentGoal,
            String currentStage,
            List<String> completed,
            List<String> unfinished,
            WorkingTreeSummary workingTree,
            List<String> risks,
            String nextStepOnly,
            String takeoverBrief,
            List<String> stackEvidence,
            List<String> runnableCommands,
            List<String> verificationGaps
    ) {
    }

    public record WorkingTreeSummary(
            boolean isGitRepository,
            boolean hasUncommittedChanges,
            String statusShort,
            List<String> latestCommits
    ) {
    }

    public record ProjectEvidence(
            String type,
            String path,
            String command,
            String summary
    ) {
    }

    public record ProjectReadLog(
            List<String> readFiles,
            List<SkippedFile> skippedFiles,
            List<String> commands
    ) {
    }

    public record SkippedFile(
            String path,
            String reason
    ) {
    }
}
