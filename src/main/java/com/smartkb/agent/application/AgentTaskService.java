package com.smartkb.agent.application;

import com.smartkb.agent.domain.AgentTaskEvent;
import com.smartkb.agent.domain.AgentTaskException;
import com.smartkb.agent.domain.AgentTaskResponse;
import com.smartkb.agent.domain.AgentTaskStatus;
import com.smartkb.agent.domain.CreateAgentTaskRequest;
import com.smartkb.agent.domain.TransitionAgentTaskRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AgentTaskService {

    private static final Map<AgentTaskStatus, AgentTaskStatus> NEXT_STATUS = new EnumMap<>(AgentTaskStatus.class);

    static {
        NEXT_STATUS.put(AgentTaskStatus.INTAKE, AgentTaskStatus.PLAN);
        NEXT_STATUS.put(AgentTaskStatus.PLAN, AgentTaskStatus.EXECUTE);
        NEXT_STATUS.put(AgentTaskStatus.EXECUTE, AgentTaskStatus.VERIFY);
        NEXT_STATUS.put(AgentTaskStatus.VERIFY, AgentTaskStatus.RECORD);
    }

    private final ConcurrentMap<String, MutableAgentTask> tasks = new ConcurrentHashMap<>();

    public AgentTaskResponse create(CreateAgentTaskRequest request) {
        String title = requireText(request == null ? null : request.title(), "TASK_TITLE_REQUIRED", "title is required");
        String goal = requireText(request == null ? null : request.goal(), "TASK_GOAL_REQUIRED", "goal is required");
        String now = now();
        MutableAgentTask task = new MutableAgentTask(
                UUID.randomUUID().toString(),
                normalize(request == null ? null : request.projectId()),
                title,
                goal,
                AgentTaskStatus.INTAKE,
                normalizeOrDefault(request == null ? null : request.riskLevel(), "medium"),
                null,
                null,
                null,
                now,
                now,
                new ArrayList<>()
        );
        task.events.add(new AgentTaskEvent(AgentTaskStatus.INTAKE, "Task created", now));
        tasks.put(task.id, task);
        return toResponse(task);
    }

    public AgentTaskResponse get(String id) {
        return toResponse(find(id));
    }

    public List<AgentTaskResponse> list() {
        return tasks.values().stream()
                .map(this::toResponse)
                .toList();
    }

    public AgentTaskResponse transition(String id, TransitionAgentTaskRequest request) {
        MutableAgentTask task = find(id);
        AgentTaskStatus targetStatus = request == null ? null : request.targetStatus();
        if (targetStatus == null) {
            throw new AgentTaskException("TASK_TARGET_STATUS_REQUIRED", HttpStatus.BAD_REQUEST, "targetStatus is required");
        }

        synchronized (task) {
            validateTransition(task.status, targetStatus, request.verificationPassed());

            task.status = targetStatus;
            task.plan = firstNonBlank(request.plan(), task.plan);
            task.verification = firstNonBlank(request.verification(), task.verification);
            task.resultSummary = firstNonBlank(request.resultSummary(), task.resultSummary);
            task.updatedAt = now();
            task.events.add(new AgentTaskEvent(targetStatus, normalize(request.note()), task.updatedAt));
            return toResponse(task);
        }
    }

    private void validateTransition(AgentTaskStatus current, AgentTaskStatus target, Boolean verificationPassed) {
        if (current == AgentTaskStatus.RECORD || current == AgentTaskStatus.BLOCKED || current == AgentTaskStatus.FAILED) {
            throw new AgentTaskException("TASK_TERMINAL_STATUS", HttpStatus.CONFLICT, "terminal task cannot transition");
        }
        if (target == AgentTaskStatus.BLOCKED || target == AgentTaskStatus.FAILED) {
            return;
        }
        AgentTaskStatus expected = NEXT_STATUS.get(current);
        if (target != expected) {
            throw new AgentTaskException(
                    "TASK_INVALID_TRANSITION",
                    HttpStatus.CONFLICT,
                    "task must transition from " + current + " to " + expected
            );
        }
        if (target == AgentTaskStatus.RECORD && !Boolean.TRUE.equals(verificationPassed)) {
            throw new AgentTaskException(
                    "TASK_VERIFICATION_REQUIRED",
                    HttpStatus.CONFLICT,
                    "verificationPassed=true is required before RECORD"
            );
        }
    }

    private MutableAgentTask find(String id) {
        String taskId = requireText(id, "TASK_ID_REQUIRED", "id is required");
        MutableAgentTask task = tasks.get(taskId);
        if (task == null) {
            throw new AgentTaskException("TASK_NOT_FOUND", HttpStatus.NOT_FOUND, "task not found");
        }
        return task;
    }

    private AgentTaskResponse toResponse(MutableAgentTask task) {
        return new AgentTaskResponse(
                task.id,
                task.projectId,
                task.title,
                task.goal,
                task.status,
                task.riskLevel,
                task.plan,
                task.verification,
                task.resultSummary,
                task.createdAt,
                task.updatedAt,
                List.copyOf(task.events)
        );
    }

    private String requireText(String value, String code, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new AgentTaskException(code, HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeOrDefault(String value, String fallback) {
        String normalized = normalize(value);
        return normalized == null ? fallback : normalized;
    }

    private String firstNonBlank(String candidate, String fallback) {
        String normalized = normalize(candidate);
        return normalized == null ? fallback : normalized;
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String now() {
        return OffsetDateTime.now().toString();
    }

    private static class MutableAgentTask {
        private final String id;
        private final String projectId;
        private final String title;
        private final String goal;
        private AgentTaskStatus status;
        private final String riskLevel;
        private String plan;
        private String verification;
        private String resultSummary;
        private final String createdAt;
        private String updatedAt;
        private final List<AgentTaskEvent> events;

        private MutableAgentTask(
                String id,
                String projectId,
                String title,
                String goal,
                AgentTaskStatus status,
                String riskLevel,
                String plan,
                String verification,
                String resultSummary,
                String createdAt,
                String updatedAt,
                List<AgentTaskEvent> events
        ) {
            this.id = id;
            this.projectId = projectId;
            this.title = title;
            this.goal = goal;
            this.status = status;
            this.riskLevel = riskLevel;
            this.plan = plan;
            this.verification = verification;
            this.resultSummary = resultSummary;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.events = events;
        }
    }
}
