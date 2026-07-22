package com.smartkb.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalRequestTest {

    @Test
    void shouldCopyOptionalDocumentFilter() {
        UUID documentId = UUID.randomUUID();
        List<UUID> documentIds = new ArrayList<>(List.of(documentId));
        RetrievalRequest request = new RetrievalRequest(
                "what is reciprocal rank fusion", UUID.randomUUID(), documentIds, 20);
        documentIds.clear();

        assertThat(request.documentIds()).containsExactly(documentId);
        assertThatThrownBy(() -> request.documentIds().add(UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectBlankQueryAndInvalidCandidateLimit() {
        UUID knowledgeBaseId = UUID.randomUUID();

        assertThatThrownBy(() -> new RetrievalRequest(" ", knowledgeBaseId, List.of(), 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("query must not be blank");
        assertThatThrownBy(() -> new RetrievalRequest("query", knowledgeBaseId, List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("candidateTopK must be positive");
    }
}
