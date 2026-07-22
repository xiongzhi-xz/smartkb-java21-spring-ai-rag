package com.smartkb.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexableChunkTest {

    @Test
    void shouldUseChunkIdAsStableIdempotentIndexKey() {
        UUID chunkId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();
        IndexableChunk first = chunk(chunkId, documentId, knowledgeBaseId, List.of(0.1f, 0.2f));
        IndexableChunk retry = chunk(chunkId, documentId, knowledgeBaseId, List.of(0.1f, 0.2f));

        assertThat(first).isEqualTo(retry);
        assertThat(first.indexId()).isEqualTo(chunkId.toString());
        assertThat(retry.indexId()).isEqualTo(first.indexId());
    }

    @Test
    void shouldDefensivelyCopyEmbeddingPayload() {
        List<Float> embedding = new ArrayList<>(List.of(0.1f, 0.2f));
        IndexableChunk chunk = chunk(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), embedding);
        embedding.add(0.3f);

        assertThat(chunk.embedding()).containsExactly(0.1f, 0.2f);
        assertThatThrownBy(() -> chunk.embedding().add(0.3f))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectInvalidIdentityPayload() {
        assertThatThrownBy(() -> new IndexableChunk(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0, 0,
                "hash", "content", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("versionNo must be positive");
    }

    private IndexableChunk chunk(
            UUID chunkId,
            UUID documentId,
            UUID knowledgeBaseId,
            List<Float> embedding) {
        return new IndexableChunk(
                chunkId,
                documentId,
                knowledgeBaseId,
                1,
                0,
                "a".repeat(64),
                "retrieval payload",
                embedding);
    }
}
