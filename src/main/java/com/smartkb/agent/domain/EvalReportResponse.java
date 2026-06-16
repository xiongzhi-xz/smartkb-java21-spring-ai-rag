package com.smartkb.agent.domain;

import java.util.List;

public record EvalReportResponse(
        String projectId,
        int totalRuns,
        int passedRuns,
        int partialRuns,
        int failedRuns,
        double successRate,
        Double scoreRate,
        Double averageDurationSeconds,
        int totalHumanInterventions,
        int totalToolCallCount,
        List<EvalCaseReportItem> cases,
        List<EvalFailureReasonSummary> failureReasons,
        String generatedAt
) {
}
