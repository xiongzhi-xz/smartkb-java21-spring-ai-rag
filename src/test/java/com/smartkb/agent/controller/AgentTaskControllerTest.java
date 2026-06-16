package com.smartkb.agent.controller;

import com.smartkb.agent.application.AgentTaskService;
import com.smartkb.agent.domain.AgentTaskEvent;
import com.smartkb.agent.domain.AgentTaskException;
import com.smartkb.agent.domain.AgentTaskResponse;
import com.smartkb.agent.domain.AgentTaskStatus;
import com.smartkb.agent.domain.CreateAgentTaskRequest;
import com.smartkb.agent.domain.TransitionAgentTaskRequest;
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

@WebMvcTest(AgentTaskController.class)
@Import(GlobalExceptionHandler.class)
class AgentTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentTaskService agentTaskService;

    @Test
    void shouldCreateTask() throws Exception {
        when(agentTaskService.create(any(CreateAgentTaskRequest.class))).thenReturn(task(AgentTaskStatus.INTAKE));

        mockMvc.perform(post("/api/agent/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "ticket-project",
                                  "title": "Run k6 benchmark",
                                  "goal": "Run first local benchmark",
                                  "riskLevel": "low"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("task-1"))
                .andExpect(jsonPath("$.status").value("INTAKE"))
                .andExpect(jsonPath("$.events[0].status").value("INTAKE"));

        ArgumentCaptor<CreateAgentTaskRequest> captor = ArgumentCaptor.forClass(CreateAgentTaskRequest.class);
        verify(agentTaskService).create(captor.capture());
        assertEquals("ticket-project", captor.getValue().projectId());
        assertEquals("Run k6 benchmark", captor.getValue().title());
    }

    @Test
    void shouldTransitionTask() throws Exception {
        when(agentTaskService.transition(eq("task-1"), any(TransitionAgentTaskRequest.class)))
                .thenReturn(task(AgentTaskStatus.PLAN));

        mockMvc.perform(post("/api/agent/tasks/task-1/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetStatus": "PLAN",
                                  "note": "Plan finished",
                                  "plan": "Run k6 only"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLAN"));

        ArgumentCaptor<TransitionAgentTaskRequest> captor = ArgumentCaptor.forClass(TransitionAgentTaskRequest.class);
        verify(agentTaskService).transition(eq("task-1"), captor.capture());
        assertEquals(AgentTaskStatus.PLAN, captor.getValue().targetStatus());
        assertEquals("Run k6 only", captor.getValue().plan());
    }

    @Test
    void shouldListTasks() throws Exception {
        when(agentTaskService.list()).thenReturn(List.of(task(AgentTaskStatus.INTAKE)));

        mockMvc.perform(get("/api/agent/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("task-1"));
    }

    @Test
    void shouldReturnAgentTaskErrorResponse() throws Exception {
        when(agentTaskService.get("missing")).thenThrow(
                new AgentTaskException("TASK_NOT_FOUND", HttpStatus.NOT_FOUND, "task not found")
        );

        mockMvc.perform(get("/api/agent/tasks/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"))
                .andExpect(jsonPath("$.error").value("task not found"));
    }

    private AgentTaskResponse task(AgentTaskStatus status) {
        return new AgentTaskResponse(
                "task-1",
                "ticket-project",
                "Run k6 benchmark",
                "Run first local benchmark",
                status,
                "low",
                "Run k6 only",
                null,
                null,
                "2026-06-17T03:30:00+08:00",
                "2026-06-17T03:30:00+08:00",
                List.of(new AgentTaskEvent(AgentTaskStatus.INTAKE, "Task created", "2026-06-17T03:30:00+08:00"))
        );
    }
}
