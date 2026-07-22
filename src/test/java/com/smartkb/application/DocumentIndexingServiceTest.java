package com.smartkb.application;

import com.smartkb.application.port.outbound.DenseVectorIndex;
import com.smartkb.application.port.outbound.DocumentChunkRepository;
import com.smartkb.application.port.outbound.KeywordIndex;
import com.smartkb.domain.IndexableChunk;
import com.smartkb.domain.KnowledgeDocument;
import com.smartkb.domain.KnowledgeDocumentStatus;
import com.smartkb.service.DocumentLoaderService;
import com.smartkb.service.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentIndexingServiceTest {

    @Test
    void shouldUseStableChunkIdsAndFinalizeOnlyAfterBothIndexes() {
        DocumentLoaderService loader = mock(DocumentLoaderService.class);
        EmbeddingService embedding = mock(EmbeddingService.class);
        DocumentChunkRepository chunks = mock(DocumentChunkRepository.class);
        DenseVectorIndex dense = mock(DenseVectorIndex.class);
        KeywordIndex keyword = mock(KeywordIndex.class);
        Document first = new Document("first chunk");
        first.setEmbedding(List.of(0.1, 0.2));
        Document second = new Document("second chunk");
        second.setEmbedding(List.of(0.3, 0.4));
        List<Document> parsed = List.of(first, second);
        KnowledgeDocument document = document();
        when(loader.loadAndSplitDocument(any(), org.mockito.ArgumentMatchers.eq("md"))).thenReturn(parsed);
        when(chunks.persistOrVerify(any(), any(), org.mockito.ArgumentMatchers.eq(1), any())).thenAnswer(call -> call.getArgument(3));

        new DocumentIndexingService(loader, embedding, chunks, dense, keyword)
                .index(document, new ByteArrayResource(new byte[0]), "md");

        var payload = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(dense).upsert(payload.capture());
        @SuppressWarnings("unchecked") List<IndexableChunk> indexed = payload.getValue();
        assertThat(indexed).extracting(IndexableChunk::chunkId)
                .containsExactly(UUID.nameUUIDFromBytes((document.id() + ":0").getBytes()),
                        UUID.nameUUIDFromBytes((document.id() + ":1").getBytes()));
        assertThat(indexed).extracting(IndexableChunk::contentHash).doesNotHaveDuplicates();
        inOrder(chunks, dense, keyword).verify(chunks).persistOrVerify(any(), any(), org.mockito.ArgumentMatchers.eq(1), any());
        inOrder(chunks, dense, keyword).verify(dense).upsert(any());
        inOrder(chunks, dense, keyword).verify(keyword).upsert(any());
        inOrder(chunks, dense, keyword).verify(chunks).markReady(any(), any());
    }

    @Test
    void shouldNotFinalizeWhenOpenSearchFails() {
        DocumentLoaderService loader = mock(DocumentLoaderService.class);
        EmbeddingService embedding = mock(EmbeddingService.class);
        DocumentChunkRepository chunks = mock(DocumentChunkRepository.class);
        DenseVectorIndex dense = mock(DenseVectorIndex.class);
        KeywordIndex keyword = mock(KeywordIndex.class);
        Document parsed = new Document("chunk");
        parsed.setEmbedding(List.of(0.1));
        when(loader.loadAndSplitDocument(any(), any())).thenReturn(List.of(parsed));
        when(chunks.persistOrVerify(any(), any(), any(Integer.class), any())).thenAnswer(call -> call.getArgument(3));
        doThrow(new IllegalStateException("unavailable")).when(keyword).upsert(any());

        assertThatThrownBy(() -> new DocumentIndexingService(loader, embedding, chunks, dense, keyword)
                .index(document(), new ByteArrayResource(new byte[0]), "md"))
                .isInstanceOf(IndexingFailureException.class)
                .extracting("errorCode").isEqualTo("OPENSEARCH_INDEX_FAILED");
        org.mockito.Mockito.verify(chunks, org.mockito.Mockito.never()).markReady(any(), any());
    }

    private KnowledgeDocument document() {
        return new KnowledgeDocument(UUID.randomUUID(), UUID.randomUUID(), "demo.md", "text/markdown",
                "documents/demo.md", "a".repeat(64), 1, 1, KnowledgeDocumentStatus.PROCESSING);
    }
}
