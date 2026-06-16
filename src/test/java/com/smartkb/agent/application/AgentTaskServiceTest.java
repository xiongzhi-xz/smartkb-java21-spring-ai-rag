package com.smartkb.agent.application;

import com.smartkb.agent.domain.AgentTaskException;
import com.smartkb.agent.domain.AgentTaskResponse;
import com.smartkb.agent.domain.AgentTaskStatus;
import com.smartkb.agent.domain.CreateAgentTaskRequest;
import com.smartkb.agent.domain.TransitionAgentTaskRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTaskServiceTest {

    private final AgentTaskService service = new AgentTaskService();

    @Test
    void shouldCreateTaskInIntakeStatus() {
        AgentTaskResponse task = service.create(new CreateAgentTaskRequest(
                "ticket-project",
                "Run TicketRush k6 benchmark",
                "Run the first local k6 benchmark",
                "low"
        ));

        assertEquals("ticket-project", task.projectId());
        assertEquals("Run TicketRush k6 benchmark", task.title());
        assertEquals(AgentTaskStatus.INTAKE, task.status());
        assertEquals("low", task.riskLevel());
        assertEquals(1, task.events().size());
        assertEquals(AgentTaskStatus.INTAKE, task.events().getFirst().status());
    }

    @Test
    void shouldMoveThroughRequiredTaskStates() {
        AgentTaskResponse task = service.create(new CreateAgentTaskRequest(
                "ticket-project",
                "Run TicketRush k6 benchmark",
                "Run the first local k6 benchmark",
                null
        ));

        task = service.transition(task.id(), new TransitionAgentTaskRequest(
                AgentTaskStatus.PLAN,
                "Scope k6 scripts only",
                "Run three inventory strategies",
                null,
                null,
                null
        ));
        assertEquals(AgentTaskStatus.PLAN, task.status());
        assertEquals("Run three inventory strategies", task.plan());

        task = service.transition(task.id(), new TransitionAgentTaskRequest(
                AgentTaskStatus.EXECUTE,
                "Execute local benchmark",
                null,
                null,
                null,
                null
        ));
        assertEquals(AgentTaskStatus.EXECUTE, task.status());

        task = service.transition(task.id(), new TransitionAgentTaskRequest(
                AgentTaskStatus.VERIFY,
                "Collect k6 summary",
                null,
                "k6 summary generated",
                null,
                null
        ));
        assertEquals(AgentTaskStatus.VERIFY, task.status());
        assertEquals("k6 summary generated", task.verification());

        task = service.transition(task.id(), new TransitionAgentTaskRequest(
                AgentTaskStatus.RECORD,
                "Record benchmark result",
                null,
                null,
                "First benchmark recorded",
                true
        ));
        assertEquals(AgentTaskStatus.RECORD, task.status());
        assertEquals("First benchmark recorded", task.resultSummary());
        assertEquals(5, task.events().size());
    }

    @Test
    void shouldRejectSkippedTransition() {
        AgentTaskResponse task = service.create(new CreateAgentTaskRequest(
                null,
                "Skipped task",
                "Try to skip plan",
                null
        ));

        AgentTaskException exception = assertThrows(
                AgentTaskException.class,
                () -> service.transition(task.id(), new TransitionAgentTaskRequest(
                        AgentTaskStatus.EXECUTE,
                        null,
                        null,
                        null,
                        null,
                        null
                ))
        );

        assertEquals("TASK_INVALID_TRANSITION", exception.code());
    }

    @Test
    void shouldRejectRecordWithoutPassingVerification() {
        AgentTaskResponse task = service.create(new CreateAgentTaskRequest(
                null,
                "Verification task",
                "Verify before record",
                null
        ));
        task = service.transition(task.id(), new TransitionAgentTaskRequest(
                AgentTaskStatus.PLAN,
                null,
                null,
                null,
                null,
                null
        ));
        task = service.transition(task.id(), new TransitionAgentTaskRequest(
                AgentTaskStatus.EXECUTE,
                null,
                null,
                null,
                null,
                null
        ));
        task = service.transition(task.id(), new TransitionAgentTaskRequest(
                AgentTaskStatus.VERIFY,
                null,
                null,
                null,
                null,
                null
        ));

        String taskId = task.id();
        AgentTaskException exception = assertThrows(
                AgentTaskException.class,
                () -> service.transition(taskId, new TransitionAgentTaskRequest(
                        AgentTaskStatus.RECORD,
                        null,
                        null,
                        null,
                        null,
                        false
                ))
        );

        assertEquals("TASK_VERIFICATION_REQUIRED", exception.code());
    }

    @Test
    void shouldKeepFailedTaskTerminal() {
        AgentTaskResponse task = service.create(new CreateAgentTaskRequest(
                null,
                "Failed task",
                "Record failure",
                null
        ));

        task = service.transition(task.id(), new TransitionAgentTaskRequest(
                AgentTaskStatus.FAILED,
                "Validation failed",
                null,
                "mvn test failed",
                null,
                false
        ));
        assertEquals(AgentTaskStatus.FAILED, task.status());
        assertTrue(task.events().stream().anyMatch(event -> event.status() == AgentTaskStatus.FAILED));

        String taskId = task.id();
        AgentTaskException exception = assertThrows(
                AgentTaskException.class,
                () -> service.transition(taskId, new TransitionAgentTaskRequest(
                        AgentTaskStatus.RECORD,
                        null,
                        null,
                        null,
                        null,
                        true
                ))
        );
        assertEquals("TASK_TERMINAL_STATUS", exception.code());
    }
}
