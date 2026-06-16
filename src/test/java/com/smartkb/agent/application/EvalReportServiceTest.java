package com.smartkb.agent.application;

import com.smartkb.agent.domain.CreateEvalCaseRunRequest;
import com.smartkb.agent.domain.EvalCaseRunStatus;
import com.smartkb.agent.domain.EvalReportResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EvalReportServiceTest {

    private final EvalCaseRunService runService = new EvalCaseRunService();
    private final EvalReportService reportService = new EvalReportService(runService);

    @Test
    void shouldGenerateAggregateReport() {
        runService.create(run("ticket-project", "E01", EvalCaseRunStatus.PASSED, 9, 10, 0, 12, 100L, null));
        runService.create(run("ticket-project", "E02", EvalCaseRunStatus.PARTIAL, 6, 10, 1, 8, 200L, "Missing full evidence"));
        runService.create(run("ticket-project", "E03", EvalCaseRunStatus.FAILED, 2, 10, 2, 4, 300L, "Build failed"));
        runService.create(run("other-project", "E01", EvalCaseRunStatus.PASSED, 10, 10, 0, 5, 50L, null));

        EvalReportResponse report = reportService.generate("ticket-project");

        assertEquals("ticket-project", report.projectId());
        assertEquals(3, report.totalRuns());
        assertEquals(1, report.passedRuns());
        assertEquals(1, report.partialRuns());
        assertEquals(1, report.failedRuns());
        assertEquals(0.3333, report.successRate());
        assertEquals(0.5667, report.scoreRate());
        assertEquals(200.0, report.averageDurationSeconds());
        assertEquals(3, report.totalHumanInterventions());
        assertEquals(24, report.totalToolCallCount());
        assertEquals(3, report.cases().size());
        assertEquals("E01", report.cases().getFirst().caseId());
        assertEquals(2, report.failureReasons().size());
        assertEquals("Build failed", report.failureReasons().getFirst().reason());
    }

    @Test
    void shouldUseLatestRunForCaseSummary() {
        runService.create(run("ticket-project", "E01", EvalCaseRunStatus.FAILED, 3, 10, 1, 6, 150L, "Missing data"));
        runService.create(run("ticket-project", "E01", EvalCaseRunStatus.PASSED, 9, 10, 0, 9, 120L, null));

        EvalReportResponse report = reportService.generate("ticket-project");

        assertEquals(1, report.cases().size());
        assertEquals(EvalCaseRunStatus.PASSED, report.cases().getFirst().latestStatus());
        assertNull(report.cases().getFirst().latestFailureReason());
    }

    @Test
    void shouldReturnEmptyReport() {
        EvalReportResponse report = reportService.generate("missing-project");

        assertEquals("missing-project", report.projectId());
        assertEquals(0, report.totalRuns());
        assertEquals(0.0, report.successRate());
        assertNull(report.scoreRate());
        assertNull(report.averageDurationSeconds());
        assertEquals(List.of(), report.cases());
        assertEquals(List.of(), report.failureReasons());
    }

    private CreateEvalCaseRunRequest run(
            String projectId,
            String caseId,
            EvalCaseRunStatus status,
            int score,
            int maxScore,
            int humanInterventions,
            int toolCallCount,
            long durationSeconds,
            String failureReason
    ) {
        return new CreateEvalCaseRunRequest(
                projectId,
                caseId,
                "Eval " + caseId,
                status,
                score,
                maxScore,
                humanInterventions,
                toolCallCount,
                durationSeconds,
                List.of("docs/agent-eval-report.md"),
                List.of("git diff --check"),
                "Recorded " + caseId,
                failureReason
        );
    }
}
