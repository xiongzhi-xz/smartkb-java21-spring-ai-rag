package com.smartkb.agent.application;

import com.smartkb.agent.domain.CreateEvalCaseRunRequest;
import com.smartkb.agent.domain.EvalCaseRunException;
import com.smartkb.agent.domain.EvalCaseRunResponse;
import com.smartkb.agent.domain.EvalCaseRunStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalCaseRunServiceTest {

    private final EvalCaseRunService service = new EvalCaseRunService();

    @Test
    void shouldCreateEvalCaseRun() {
        EvalCaseRunResponse run = service.create(new CreateEvalCaseRunRequest(
                "ticket-project",
                "E01",
                "TicketRush intake",
                EvalCaseRunStatus.PASSED,
                9,
                10,
                0,
                12,
                180L,
                List.of("HANDOFF.md", "docs/agent-eval-report.md", "HANDOFF.md"),
                List.of("git status --short", "mvn test"),
                "Intake summary recorded",
                null
        ));

        assertEquals("ticket-project", run.projectId());
        assertEquals("E01", run.caseId());
        assertEquals(EvalCaseRunStatus.PASSED, run.status());
        assertEquals(9, run.score());
        assertEquals(2, run.evidencePaths().size());
        assertTrue(run.createdAt().contains("T"));
    }

    @Test
    void shouldListRunsWithFilters() {
        service.create(run("ticket-project", "E01", EvalCaseRunStatus.PASSED));
        service.create(run("ticket-project", "E02", EvalCaseRunStatus.PARTIAL));
        service.create(run("other-project", "E01", EvalCaseRunStatus.FAILED));

        List<EvalCaseRunResponse> projectRuns = service.list("ticket-project", null, null);
        List<EvalCaseRunResponse> caseRuns = service.list(null, "E01", null);
        List<EvalCaseRunResponse> statusRuns = service.list(null, null, EvalCaseRunStatus.PARTIAL);

        assertEquals(2, projectRuns.size());
        assertEquals(2, caseRuns.size());
        assertEquals(1, statusRuns.size());
        assertEquals("E02", statusRuns.getFirst().caseId());
    }

    @Test
    void shouldRejectMissingRequiredFields() {
        EvalCaseRunException exception = assertThrows(
                EvalCaseRunException.class,
                () -> service.create(new CreateEvalCaseRunRequest(
                        null,
                        " ",
                        "TicketRush intake",
                        EvalCaseRunStatus.PASSED,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ))
        );

        assertEquals("EVAL_CASE_ID_REQUIRED", exception.code());
    }

    @Test
    void shouldRejectInvalidScoreValues() {
        EvalCaseRunException exception = assertThrows(
                EvalCaseRunException.class,
                () -> service.create(new CreateEvalCaseRunRequest(
                        null,
                        "E01",
                        "TicketRush intake",
                        EvalCaseRunStatus.PASSED,
                        11,
                        10,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ))
        );

        assertEquals("EVAL_SCORE_EXCEEDS_MAX", exception.code());
    }

    @Test
    void shouldReturnRunById() {
        EvalCaseRunResponse created = service.create(run("ticket-project", "E01", EvalCaseRunStatus.PASSED));

        EvalCaseRunResponse found = service.get(created.id());

        assertEquals(created.id(), found.id());
    }

    @Test
    void shouldRejectMissingRun() {
        EvalCaseRunException exception = assertThrows(
                EvalCaseRunException.class,
                () -> service.get("missing")
        );

        assertEquals("EVAL_RUN_NOT_FOUND", exception.code());
    }

    private CreateEvalCaseRunRequest run(String projectId, String caseId, EvalCaseRunStatus status) {
        return new CreateEvalCaseRunRequest(
                projectId,
                caseId,
                "Eval " + caseId,
                status,
                8,
                10,
                1,
                10,
                120L,
                List.of("docs/agent-eval-report.md"),
                List.of("git diff --check"),
                "Recorded " + caseId,
                status == EvalCaseRunStatus.FAILED ? "Missing evidence" : null
        );
    }
}
