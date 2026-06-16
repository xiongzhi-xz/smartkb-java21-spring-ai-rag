package com.smartkb.agent.domain;

import java.util.List;

public record CreateEvalCaseRunRequest(
        String projectId,
        String caseId,
        String title,
        EvalCaseRunStatus status,
        Integer score,
        Integer maxScore,
        Integer humanInterventions,
        Integer toolCallCount,
        Long durationSeconds,
        List<String> evidencePaths,
        List<String> verificationCommands,
        String summary,
        String failureReason
) {
}
