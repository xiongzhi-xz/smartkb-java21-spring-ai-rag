package com.smartkb.domain;

import java.util.Objects;
import java.util.UUID;

/** A backend-ranked candidate before cross-index fusion and reranking. */
public record RetrievalCandidate(
        UUID chunkId,
        UUID documentId,
        UUID knowledgeBaseId,
        int versionNo,
        int ordinal,
        String content,
        double score) {

    public RetrievalCandidate {
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(knowledgeBaseId, "knowledgeBaseId must not be null");
        if (versionNo < 1) {
            throw new IllegalArgumentException("versionNo must be positive");
        }
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must not be negative");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }
    }
}
