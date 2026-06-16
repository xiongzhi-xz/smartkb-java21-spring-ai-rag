package com.smartkb.agent.application;

import com.smartkb.agent.domain.ImportEvalCaseRunsResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvalCaseRunImportServiceTest {

    private final EvalCaseRunService runService = new EvalCaseRunService();
    private final EvalCaseRunImportService importService = new EvalCaseRunImportService(runService);

    @Test
    void shouldImportTicketRushReportRuns() {
        ImportEvalCaseRunsResponse response = importService.importTicketRushReport();

        assertEquals(10, response.importedCount());
        assertEquals(0, response.skippedCount());
        assertEquals(10, response.runs().size());
        assertEquals(10, runService.list("ticket-project", null, null).size());
        assertEquals(2, response.runs().getFirst().score());
        assertEquals("docs/agent-eval-report.md", response.runs().getFirst().evidencePaths().getFirst());
    }

    @Test
    void shouldSkipAlreadyImportedTicketRushRuns() {
        importService.importTicketRushReport();

        ImportEvalCaseRunsResponse response = importService.importTicketRushReport();

        assertEquals(0, response.importedCount());
        assertEquals(10, response.skippedCount());
        assertEquals(10, runService.list("ticket-project", null, null).size());
    }
}
