package com.smartkb.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Query and metadata boundary shared by both retrieval backends. */
public record RetrievalRequest(
        String query,
        UUID knowledgeBaseId,
        List<UUID> documentIds,
        int candidateTopK) {

    public RetrievalRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        Objects.requireNonNull(knowledgeBaseId, "knowledgeBaseId must not be null");
        documentIds = documentIds == null ? List.of() : List.copyOf(documentIds);
        if (documentIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("documentIds must not contain null values");
        }
        if (candidateTopK < 1) {
            throw new IllegalArgumentException("candidateTopK must be positive");
        }
    }
}
