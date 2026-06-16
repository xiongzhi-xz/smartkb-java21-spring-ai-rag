package com.smartkb.agent.controller;

import com.smartkb.agent.application.EvalCaseRunService;
import com.smartkb.agent.domain.CreateEvalCaseRunRequest;
import com.smartkb.agent.domain.EvalCaseRunException;
import com.smartkb.agent.domain.EvalCaseRunResponse;
import com.smartkb.agent.domain.EvalCaseRunStatus;
import com.smartkb.config.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EvalCaseRunController.class)
@Import(GlobalExceptionHandler.class)
class EvalCaseRunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EvalCaseRunService evalCaseRunService;

    @Test
    void shouldCreateRun() throws Exception {
        when(evalCaseRunService.create(any(CreateEvalCaseRunRequest.class))).thenReturn(run(EvalCaseRunStatus.PASSED));

        mockMvc.perform(post("/api/agent/eval/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "ticket-project",
                                  "caseId": "E01",
                                  "title": "TicketRush intake",
                                  "status": "PASSED",
                                  "score": 9,
                                  "maxScore": 10,
                                  "humanInterventions": 0,
                                  "toolCallCount": 12,
                                  "durationSeconds": 180,
                                  "evidencePaths": ["HANDOFF.md"],
                                  "verificationCommands": ["git status --short"],
                                  "summary": "Intake recorded"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("run-1"))
                .andExpect(jsonPath("$.caseId").value("E01"))
                .andExpect(jsonPath("$.status").value("PASSED"));

        ArgumentCaptor<CreateEvalCaseRunRequest> captor = ArgumentCaptor.forClass(CreateEvalCaseRunRequest.class);
        verify(evalCaseRunService).create(captor.capture());
        assertEquals("ticket-project", captor.getValue().projectId());
        assertEquals(EvalCaseRunStatus.PASSED, captor.getValue().status());
    }

    @Test
    void shouldListRunsWithFilters() throws Exception {
        when(evalCaseRunService.list("ticket-project", "E01", EvalCaseRunStatus.PASSED))
                .thenReturn(List.of(run(EvalCaseRunStatus.PASSED)));

        mockMvc.perform(get("/api/agent/eval/runs")
                        .param("projectId", "ticket-project")
                        .param("caseId", "E01")
                        .param("status", "PASSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("run-1"));

        verify(evalCaseRunService).list("ticket-project", "E01", EvalCaseRunStatus.PASSED);
    }

    @Test
    void shouldGetRun() throws Exception {
        when(evalCaseRunService.get("run-1")).thenReturn(run(EvalCaseRunStatus.PARTIAL));

        mockMvc.perform(get("/api/agent/eval/runs/run-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("run-1"))
                .andExpect(jsonPath("$.status").value("PARTIAL"));

        verify(evalCaseRunService).get(eq("run-1"));
    }

    @Test
    void shouldReturnEvalRunErrorResponse() throws Exception {
        when(evalCaseRunService.get("missing")).thenThrow(
                new EvalCaseRunException("EVAL_RUN_NOT_FOUND", HttpStatus.NOT_FOUND, "eval run not found")
        );

        mockMvc.perform(get("/api/agent/eval/runs/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("EVAL_RUN_NOT_FOUND"))
                .andExpect(jsonPath("$.error").value("eval run not found"));
    }

    private EvalCaseRunResponse run(EvalCaseRunStatus status) {
        return new EvalCaseRunResponse(
                "run-1",
                "ticket-project",
                "E01",
                "TicketRush intake",
                status,
                9,
                10,
                0,
                12,
                180L,
                List.of("HANDOFF.md"),
                List.of("git status --short"),
                "Intake recorded",
                null,
                "2026-06-17T04:30:00+08:00"
        );
    }
}
