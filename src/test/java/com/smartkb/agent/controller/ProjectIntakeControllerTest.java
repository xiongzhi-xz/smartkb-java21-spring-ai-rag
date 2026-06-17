package com.smartkb.agent.controller;

import com.smartkb.agent.application.ProjectIntakeService;
import com.smartkb.agent.domain.ProjectIntakeException;
import com.smartkb.agent.domain.ProjectIntakeRequest;
import com.smartkb.agent.domain.ProjectIntakeResponse;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectIntakeController.class)
@Import(GlobalExceptionHandler.class)
class ProjectIntakeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProjectIntakeService projectIntakeService;

    @Test
    void shouldReturnProjectIntakeResponse() throws Exception {
        when(projectIntakeService.intake(any(ProjectIntakeRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/agent/projects/intake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rootPath": "E:/project/work/job/ticketrush-java21-high-concurrency",
                                  "goal": "intake TicketRush",
                                  "includeCodeTree": true,
                                  "maxFiles": 200,
                                  "maxFileBytes": 65536
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.project.name").value("ticketrush-java21-high-concurrency"))
                .andExpect(jsonPath("$.intake.currentStage").value("Docker Compose verified"))
                .andExpect(jsonPath("$.intake.workingTree.hasUncommittedChanges").value(false))
                .andExpect(jsonPath("$.intake.nextStepOnly").value("Run first local k6 benchmark"))
                .andExpect(jsonPath("$.intake.takeoverBrief").value("Take over TicketRush. Next: Run first local k6 benchmark"))
                .andExpect(jsonPath("$.intake.stackEvidence[0]").value("Java 21: pom.xml"))
                .andExpect(jsonPath("$.intake.runnableCommands[0]").value("mvn test"))
                .andExpect(jsonPath("$.intake.verificationGaps[0]").value("k6 benchmark"))
                .andExpect(jsonPath("$.evidence[0].path").value("HANDOFF.md"));

        ArgumentCaptor<ProjectIntakeRequest> captor = ArgumentCaptor.forClass(ProjectIntakeRequest.class);
        verify(projectIntakeService).intake(captor.capture());
        ProjectIntakeRequest request = captor.getValue();
        assertEquals("E:/project/work/job/ticketrush-java21-high-concurrency", request.rootPath());
        assertEquals("intake TicketRush", request.goal());
        assertEquals(Boolean.TRUE, request.includeCodeTree());
        assertEquals(200, request.maxFiles());
        assertEquals(65_536, request.maxFileBytes());
    }

    @Test
    void shouldReturnProjectIntakeErrorResponse() throws Exception {
        when(projectIntakeService.intake(any(ProjectIntakeRequest.class))).thenThrow(
                new ProjectIntakeException("PROJECT_PATH_REQUIRED", HttpStatus.BAD_REQUEST, "rootPath is required")
        );

        mockMvc.perform(post("/api/agent/projects/intake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PROJECT_PATH_REQUIRED"))
                .andExpect(jsonPath("$.error").value("rootPath is required"));
    }

    private ProjectIntakeResponse response() {
        return new ProjectIntakeResponse(
                true,
                new ProjectIntakeResponse.ProjectSummary(
                        "ticket-project",
                        "ticketrush-java21-high-concurrency",
                        "E:/project/work/job/ticketrush-java21-high-concurrency",
                        List.of("Java 21", "Spring Boot", "Redis", "RocketMQ"),
                        "maven",
                        List.of("pom.xml"),
                        List.of("mvn test"),
                        "2026-06-17T03:00:00+08:00"
                ),
                new ProjectIntakeResponse.IntakeSummary(
                        "Take over TicketRush",
                        "Docker Compose verified",
                        List.of("Ticket purchase flow", "35 tests passed"),
                        List.of("k6 benchmark"),
                        new ProjectIntakeResponse.WorkingTreeSummary(
                                true,
                                false,
                                "",
                                List.of("38d7c1d fix: disable nacos config health noise in docker profile")
                        ),
                        List.of("Do not add new scope"),
                        "Run first local k6 benchmark",
                        "Take over TicketRush. Next: Run first local k6 benchmark",
                        List.of("Java 21: pom.xml"),
                        List.of("mvn test"),
                        List.of("k6 benchmark")
                ),
                List.of(new ProjectIntakeResponse.ProjectEvidence(
                        "handoff",
                        "HANDOFF.md",
                        null,
                        "Current phase and next action"
                )),
                new ProjectIntakeResponse.ProjectReadLog(
                        List.of("HANDOFF.md", "pom.xml"),
                        List.of(),
                        List.of("git status --short", "git log --oneline -5")
                ),
                List.of()
        );
    }
}
