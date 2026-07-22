package com.smartkb.domain;

import java.util.List;
import java.util.Objects;

/** Enterprise retrieval output before BGE reranking in the next query-orchestration phase. */
public record EnterpriseRetrievalResult(
        String mode,
        List<FusedRetrievalCandidate> candidates,
        List<String> backendFailures) {

    public EnterpriseRetrievalResult {
        if (!List.of("hybrid", "dense-only", "keyword-only").contains(mode)) {
            throw new IllegalArgumentException("unsupported retrieval mode: " + mode);
        }
        candidates = List.copyOf(candidates);
        backendFailures = List.copyOf(backendFailures);
        if (candidates.stream().anyMatch(Objects::isNull) || backendFailures.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("result collections must not contain null values");
        }
    }
}
