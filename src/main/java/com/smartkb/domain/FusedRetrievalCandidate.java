package com.smartkb.domain;

import java.util.Objects;

/** A deterministic RRF result with the source ranks retained for retrieval tracing. */
public record FusedRetrievalCandidate(
        RetrievalCandidate candidate,
        Integer denseRank,
        Integer keywordRank,
        double fusionScore) {

    public FusedRetrievalCandidate {
        Objects.requireNonNull(candidate, "candidate must not be null");
        if (denseRank == null && keywordRank == null) {
            throw new IllegalArgumentException("at least one source rank is required");
        }
        if (denseRank != null && denseRank < 1 || keywordRank != null && keywordRank < 1) {
            throw new IllegalArgumentException("source ranks must be positive");
        }
        if (!Double.isFinite(fusionScore) || fusionScore < 0) {
            throw new IllegalArgumentException("fusionScore must be finite and non-negative");
        }
    }
}
