package com.smartkb.infrastructure.messaging;

import com.smartkb.application.DocumentIndexingService;
import com.smartkb.application.IndexingFailureException;
import com.smartkb.application.port.outbound.DocumentIngestionRepository;
import com.smartkb.application.port.outbound.ObjectStorage;
import com.smartkb.domain.IngestionRequestedEvent;
import com.smartkb.domain.KnowledgeDocument;
import com.smartkb.domain.KnowledgeDocumentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IngestionJobListenerTest {

    @Test
    void shouldProcessObjectAndMarkJobReady() throws Exception {
        DocumentIngestionRepository repository = mock(DocumentIngestionRepository.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        DocumentIndexingService indexingService = mock(DocumentIndexingService.class);
        IngestionRequestedEvent event = event(UUID.randomUUID());
        KnowledgeDocument document = document(event);
        when(repository.markProcessing(event.jobId(), event.documentId())).thenReturn(true);
        when(repository.markReady(event.jobId(), event.documentId())).thenReturn(true);
        when(repository.requireDocument(event.documentId())).thenReturn(document);
        when(objectStorage.get(event.objectKey())).thenReturn(new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)));

        new IngestionJobListener(repository, objectStorage, indexingService).consume(event);

        var resourceCaptor = org.mockito.ArgumentCaptor.forClass(Resource.class);
        verify(indexingService).index(eq(document), resourceCaptor.capture(), eq("md"));
        assertEquals("doc-1.md", resourceCaptor.getValue().getFilename());
        assertEquals("content", new String(resourceCaptor.getValue().getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        verify(repository).markReady(event.jobId(), event.documentId());
    }

    @Test
    void shouldSkipDuplicateEventBeforeReadingObject() {
        DocumentIngestionRepository repository = mock(DocumentIngestionRepository.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        DocumentIndexingService indexingService = mock(DocumentIndexingService.class);
        IngestionRequestedEvent event = event(UUID.randomUUID());
        when(repository.markProcessing(event.jobId(), event.documentId())).thenReturn(false);

        new IngestionJobListener(repository, objectStorage, indexingService).consume(event);

        verify(objectStorage, org.mockito.Mockito.never()).get(event.objectKey());
        verifyNoInteractions(indexingService);
    }

    @Test
    void shouldMarkJobFailedWhenTargetIndexWriteFails() {
        DocumentIngestionRepository repository = mock(DocumentIngestionRepository.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        DocumentIndexingService indexingService = mock(DocumentIndexingService.class);
        IngestionRequestedEvent event = event(UUID.randomUUID());
        when(repository.markProcessing(event.jobId(), event.documentId())).thenReturn(true);
        when(repository.requireDocument(event.documentId())).thenReturn(document(event));
        when(objectStorage.get(event.objectKey())).thenReturn(new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)));
        when(repository.markFailed(event.jobId(), event.documentId(), "OPENSEARCH_INDEX_FAILED", "OpenSearch upsert failed")).thenReturn(true);
        doThrow(new IndexingFailureException("OPENSEARCH_INDEX_FAILED", new IllegalStateException("OpenSearch upsert failed")))
                .when(indexingService).index(any(), any(), eq("md"));

        assertThrows(IllegalStateException.class,
                () -> new IngestionJobListener(repository, objectStorage, indexingService).consume(event));

        verify(repository).markFailed(event.jobId(), event.documentId(), "OPENSEARCH_INDEX_FAILED", "OpenSearch upsert failed");
    }

    private IngestionRequestedEvent event(UUID jobId) {
        return new IngestionRequestedEvent(jobId, UUID.randomUUID(), "upload-1", "documents/doc-1.md", "doc-1.md", "md");
    }

    private KnowledgeDocument document(IngestionRequestedEvent event) {
        return new KnowledgeDocument(event.documentId(), UUID.randomUUID(), event.fileName(), "text/markdown",
                event.objectKey(), "a".repeat(64), 7, 1, KnowledgeDocumentStatus.PROCESSING);
    }
}
