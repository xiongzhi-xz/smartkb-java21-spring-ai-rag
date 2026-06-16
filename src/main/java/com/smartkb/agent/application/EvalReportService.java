package com.smartkb.agent.application;

import com.smartkb.agent.domain.EvalCaseReportItem;
import com.smartkb.agent.domain.EvalCaseRunResponse;
import com.smartkb.agent.domain.EvalCaseRunStatus;
import com.smartkb.agent.domain.EvalFailureReasonSummary;
import com.smartkb.agent.domain.EvalReportResponse;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class EvalReportService {

    private final EvalCaseRunService evalCaseRunService;

    public EvalReportService(EvalCaseRunService evalCaseRunService) {
        this.evalCaseRunService = evalCaseRunService;
    }

    public EvalReportResponse generate(String projectId) {
        String normalizedProjectId = normalize(projectId);
        List<EvalCaseRunResponse> runs = evalCaseRunService.list(normalizedProjectId, null, null);

        int totalRuns = runs.size();
        int passedRuns = countStatus(runs, EvalCaseRunStatus.PASSED);
        int partialRuns = countStatus(runs, EvalCaseRunStatus.PARTIAL);
        int failedRuns = countStatus(runs, EvalCaseRunStatus.FAILED);

        int scoreSum = runs.stream()
                .map(EvalCaseRunResponse::score)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        int maxScoreSum = runs.stream()
                .map(EvalCaseRunResponse::maxScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        List<Long> durations = runs.stream()
                .map(EvalCaseRunResponse::durationSeconds)
                .filter(Objects::nonNull)
                .toList();

        return new EvalReportResponse(
                normalizedProjectId,
                totalRuns,
                passedRuns,
                partialRuns,
                failedRuns,
                totalRuns == 0 ? 0.0 : round((double) passedRuns / totalRuns),
                maxScoreSum == 0 ? null : round((double) scoreSum / maxScoreSum),
                durations.isEmpty() ? null : round(durations.stream().mapToLong(Long::longValue).average().orElse(0.0)),
                runs.stream().map(EvalCaseRunResponse::humanInterventions).filter(Objects::nonNull).mapToInt(Integer::intValue).sum(),
                runs.stream().map(EvalCaseRunResponse::toolCallCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum(),
                buildCases(runs),
                buildFailureReasons(runs),
                now()
        );
    }

    private int countStatus(List<EvalCaseRunResponse> runs, EvalCaseRunStatus status) {
        return (int) runs.stream()
                .filter(run -> status == run.status())
                .count();
    }

    private List<EvalCaseReportItem> buildCases(List<EvalCaseRunResponse> runs) {
        Map<String, EvalCaseRunResponse> latestByCase = new LinkedHashMap<>();
        for (EvalCaseRunResponse run : runs) {
            latestByCase.putIfAbsent(run.caseId(), run);
        }
        return latestByCase.values().stream()
                .sorted(Comparator.comparing(EvalCaseRunResponse::caseId))
                .map(run -> new EvalCaseReportItem(
                        run.caseId(),
                        run.title(),
                        run.status(),
                        run.score(),
                        run.maxScore(),
                        run.id(),
                        run.createdAt(),
                        run.failureReason()
                ))
                .toList();
    }

    private List<EvalFailureReasonSummary> buildFailureReasons(List<EvalCaseRunResponse> runs) {
        return runs.stream()
                .map(EvalCaseRunResponse::failureReason)
                .map(this::normalize)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(reason -> reason, Collectors.counting()))
                .entrySet()
                .stream()
                .map(entry -> new EvalFailureReasonSummary(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(EvalFailureReasonSummary::count).reversed()
                        .thenComparing(EvalFailureReasonSummary::reason))
                .toList();
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
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
