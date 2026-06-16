package com.smartkb.agent.application;

import com.smartkb.agent.domain.CreateEvalCaseRunRequest;
import com.smartkb.agent.domain.EvalCaseRunException;
import com.smartkb.agent.domain.EvalCaseRunResponse;
import com.smartkb.agent.domain.EvalCaseRunStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class EvalCaseRunService {

    private final ConcurrentMap<String, EvalCaseRunResponse> runs = new ConcurrentHashMap<>();

    public EvalCaseRunResponse create(CreateEvalCaseRunRequest request) {
        String caseId = requireText(request == null ? null : request.caseId(), "EVAL_CASE_ID_REQUIRED", "caseId is required");
        String title = requireText(request == null ? null : request.title(), "EVAL_TITLE_REQUIRED", "title is required");
        EvalCaseRunStatus status = requireStatus(request == null ? null : request.status());
        Integer score = normalizeNonNegative(request == null ? null : request.score(), "EVAL_SCORE_INVALID", "score must be non-negative");
        Integer maxScore = normalizePositive(request == null ? null : request.maxScore(), "EVAL_MAX_SCORE_INVALID", "maxScore must be positive");
        validateScore(score, maxScore);

        EvalCaseRunResponse run = new EvalCaseRunResponse(
                UUID.randomUUID().toString(),
                normalize(request == null ? null : request.projectId()),
                caseId,
                title,
                status,
                score,
                maxScore,
                normalizeNonNegative(request == null ? null : request.humanInterventions(), "EVAL_HUMAN_INTERVENTIONS_INVALID", "humanInterventions must be non-negative"),
                normalizeNonNegative(request == null ? null : request.toolCallCount(), "EVAL_TOOL_CALL_COUNT_INVALID", "toolCallCount must be non-negative"),
                normalizeNonNegativeLong(request == null ? null : request.durationSeconds(), "EVAL_DURATION_INVALID", "durationSeconds must be non-negative"),
                normalizeTextList(request == null ? null : request.evidencePaths()),
                normalizeTextList(request == null ? null : request.verificationCommands()),
                normalize(request == null ? null : request.summary()),
                normalize(request == null ? null : request.failureReason()),
                now()
        );
        runs.put(run.id(), run);
        return run;
    }

    public EvalCaseRunResponse get(String id) {
        String runId = requireText(id, "EVAL_RUN_ID_REQUIRED", "id is required");
        EvalCaseRunResponse run = runs.get(runId);
        if (run == null) {
            throw new EvalCaseRunException("EVAL_RUN_NOT_FOUND", HttpStatus.NOT_FOUND, "eval run not found");
        }
        return run;
    }

    public List<EvalCaseRunResponse> list(String projectId, String caseId, EvalCaseRunStatus status) {
        String normalizedProjectId = normalize(projectId);
        String normalizedCaseId = normalize(caseId);
        return runs.values().stream()
                .filter(run -> normalizedProjectId == null || normalizedProjectId.equals(run.projectId()))
                .filter(run -> normalizedCaseId == null || normalizedCaseId.equals(run.caseId()))
                .filter(run -> status == null || status == run.status())
                .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
                .toList();
    }

    private EvalCaseRunStatus requireStatus(EvalCaseRunStatus status) {
        if (status == null) {
            throw new EvalCaseRunException("EVAL_STATUS_REQUIRED", HttpStatus.BAD_REQUEST, "status is required");
        }
        return status;
    }

    private void validateScore(Integer score, Integer maxScore) {
        if (score != null && maxScore != null && score > maxScore) {
            throw new EvalCaseRunException("EVAL_SCORE_EXCEEDS_MAX", HttpStatus.BAD_REQUEST, "score cannot exceed maxScore");
        }
    }

    private Integer normalizeNonNegative(Integer value, String code, String message) {
        if (value != null && value < 0) {
            throw new EvalCaseRunException(code, HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }

    private Integer normalizePositive(Integer value, String code, String message) {
        if (value != null && value <= 0) {
            throw new EvalCaseRunException(code, HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }

    private Long normalizeNonNegativeLong(Long value, String code, String message) {
        if (value != null && value < 0) {
            throw new EvalCaseRunException(code, HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }

    private List<String> normalizeTextList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(this::normalize)
                .filter(value -> value != null)
                .distinct()
                .toList();
    }

    private String requireText(String value, String code, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new EvalCaseRunException(code, HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
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
}
