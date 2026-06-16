package com.smartkb.agent.controller;

import com.smartkb.agent.application.EvalReportService;
import com.smartkb.agent.domain.EvalCaseReportItem;
import com.smartkb.agent.domain.EvalCaseRunStatus;
import com.smartkb.agent.domain.EvalFailureReasonSummary;
import com.smartkb.agent.domain.EvalReportResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EvalReportController.class)
class EvalReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EvalReportService evalReportService;

    @Test
    void shouldGenerateReport() throws Exception {
        when(evalReportService.generate("ticket-project")).thenReturn(report());

        mockMvc.perform(get("/api/agent/eval/report")
                        .param("projectId", "ticket-project")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value("ticket-project"))
                .andExpect(jsonPath("$.totalRuns").value(3))
                .andExpect(jsonPath("$.passedRuns").value(1))
                .andExpect(jsonPath("$.successRate").value(0.3333))
                .andExpect(jsonPath("$.cases[0].caseId").value("E01"))
                .andExpect(jsonPath("$.failureReasons[0].reason").value("Build failed"));

        verify(evalReportService).generate("ticket-project");
    }

    private EvalReportResponse report() {
        return new EvalReportResponse(
                "ticket-project",
                3,
                1,
                1,
                1,
                0.3333,
                0.5667,
                200.0,
                3,
                24,
                List.of(new EvalCaseReportItem(
                        "E01",
                        "TicketRush intake",
                        EvalCaseRunStatus.PASSED,
                        9,
                        10,
                        "run-1",
                        "2026-06-17T04:50:00+08:00",
                        null
                )),
                List.of(new EvalFailureReasonSummary("Build failed", 1)),
                "2026-06-17T04:50:00+08:00"
        );
    }
}
