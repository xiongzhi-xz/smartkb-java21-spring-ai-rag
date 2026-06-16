package com.smartkb.agent.domain;

public record EvalCaseReportItem(
        String caseId,
        String title,
        EvalCaseRunStatus latestStatus,
        Integer latestScore,
        Integer latestMaxScore,
        String latestRunId,
        String latestCreatedAt,
        String latestFailureReason
) {
}
