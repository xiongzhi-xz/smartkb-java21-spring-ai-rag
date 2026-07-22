package com.smartkb.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A deterministic chunk payload shared by the dense and keyword indexes. */
public record IndexableChunk(
        UUID chunkId,
        UUID documentId,
        UUID knowledgeBaseId,
        int versionNo,
        int ordinal,
        String contentHash,
        String content,
        List<Float> embedding) {

    public IndexableChunk {
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(knowledgeBaseId, "knowledgeBaseId must not be null");
        if (versionNo < 1) {
            throw new IllegalArgumentException("versionNo must be positive");
        }
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must not be negative");
        }
        if (contentHash == null || contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash must not be blank");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        embedding = embedding == null ? List.of() : List.copyOf(embedding);
        if (embedding.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("embedding must not contain null values");
        }
    }

    /** Stable ID for Milvus primary-key and OpenSearch document-ID upserts. */
    public String indexId() {
        return chunkId.toString();
    }
}
