package com.smartkb.application;

import com.smartkb.application.port.outbound.DenseVectorIndex;
import com.smartkb.application.port.outbound.KeywordIndex;
import com.smartkb.domain.RetrievalCandidate;
import com.smartkb.domain.RetrievalRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnterpriseRetrievalServiceTest {

    @Test
    void shouldApplyWeightedRrfAndKeepSourceRanks() {
        DenseVectorIndex dense = mock(DenseVectorIndex.class);
        KeywordIndex keyword = mock(KeywordIndex.class);
        UUID knowledgeBaseId = UUID.randomUUID();
        RetrievalRequest request = new RetrievalRequest("query", knowledgeBaseId, List.of(), 20);
        RetrievalCandidate both = candidate(knowledgeBaseId, UUID.randomUUID(), UUID.randomUUID(), "both");
        RetrievalCandidate denseOnly = candidate(knowledgeBaseId, UUID.randomUUID(), UUID.randomUUID(), "dense");
        RetrievalCandidate keywordOnly = candidate(knowledgeBaseId, UUID.randomUUID(), UUID.randomUUID(), "keyword");
        when(dense.search(request)).thenReturn(List.of(denseOnly, both));
        when(keyword.search(request)).thenReturn(List.of(both, keywordOnly));

        var result = new EnterpriseRetrievalService(dense, keyword).retrieve(request);

        assertThat(result.mode()).isEqualTo("hybrid");
        assertThat(result.candidates()).extracting(item -> item.candidate().chunkId())
                .containsExactly(both.chunkId(), denseOnly.chunkId(), keywordOnly.chunkId());
        assertThat(result.candidates().getFirst().denseRank()).isEqualTo(2);
        assertThat(result.candidates().getFirst().keywordRank()).isEqualTo(1);
    }

    @Test
    void shouldDefensivelyFilterCandidatesOutsideRequestBoundary() {
        DenseVectorIndex dense = mock(DenseVectorIndex.class);
        KeywordIndex keyword = mock(KeywordIndex.class);
        UUID knowledgeBaseId = UUID.randomUUID();
        UUID requestedDocument = UUID.randomUUID();
        RetrievalRequest request = new RetrievalRequest("query", knowledgeBaseId, List.of(requestedDocument), 20);
        when(dense.search(request)).thenReturn(List.of(
                candidate(knowledgeBaseId, requestedDocument, UUID.randomUUID(), "allowed"),
                candidate(knowledgeBaseId, UUID.randomUUID(), UUID.randomUUID(), "wrong document"),
                candidate(UUID.randomUUID(), requestedDocument, UUID.randomUUID(), "wrong kb")));
        when(keyword.search(request)).thenReturn(List.of());

        var result = new EnterpriseRetrievalService(dense, keyword).retrieve(request);

        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().getFirst().candidate().documentId()).isEqualTo(requestedDocument);
    }

    @Test
    void shouldDegradeToHealthyBackendButFailWhenBothFail() {
        DenseVectorIndex dense = mock(DenseVectorIndex.class);
        KeywordIndex keyword = mock(KeywordIndex.class);
        UUID knowledgeBaseId = UUID.randomUUID();
        RetrievalRequest request = new RetrievalRequest("query", knowledgeBaseId, List.of(), 20);
        when(dense.search(request)).thenThrow(new IllegalStateException("Milvus unavailable"));
        when(keyword.search(request)).thenReturn(List.of(candidate(knowledgeBaseId, UUID.randomUUID(), UUID.randomUUID(), "keyword")));

        var result = new EnterpriseRetrievalService(dense, keyword).retrieve(request);

        assertThat(result.mode()).isEqualTo("keyword-only");
        assertThat(result.backendFailures()).containsExactly("milvus: IllegalStateException");

        when(keyword.search(request)).thenThrow(new IllegalStateException("OpenSearch unavailable"));
        assertThatThrownBy(() -> new EnterpriseRetrievalService(dense, keyword).retrieve(request))
                .isInstanceOf(RetrievalUnavailableException.class)
                .hasMessage("RETRIEVAL_UNAVAILABLE");
    }

    private RetrievalCandidate candidate(UUID knowledgeBaseId, UUID documentId, UUID chunkId, String content) {
        return new RetrievalCandidate(chunkId, documentId, knowledgeBaseId, 1, 0, content, 1.0);
    }
}
