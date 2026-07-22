package com.smartkb.infrastructure.messaging;

import com.smartkb.application.port.outbound.DocumentIngestionRepository;
import com.smartkb.application.port.outbound.ObjectStorage;
import com.smartkb.domain.IngestionRequestedEvent;
import com.smartkb.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
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
        RagService ragService = mock(RagService.class);
        UUID jobId = UUID.randomUUID();
        IngestionRequestedEvent event = event(jobId);

        when(repository.markProcessing(jobId, event.documentId())).thenReturn(true);
        when(repository.markReady(jobId, event.documentId())).thenReturn(true);
        when(objectStorage.get("documents/doc-1.md"))
                .thenReturn(new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)));

        new IngestionJobListener(repository, objectStorage, ragService).consume(event);

        var resourceCaptor = org.mockito.ArgumentCaptor.forClass(Resource.class);
        verify(ragService).addDocument(
                resourceCaptor.capture(),
                eq("md"),
                argThat(metadata -> jobId.toString().equals(metadata.get("jobId"))));
        assertEquals("doc-1.md", resourceCaptor.getValue().getFilename());
        assertEquals("content", new String(
                resourceCaptor.getValue().getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        verify(repository).markReady(jobId, event.documentId());
    }

    @Test
    void shouldSkipDuplicateEventBeforeReadingObject() {
        DocumentIngestionRepository repository = mock(DocumentIngestionRepository.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        RagService ragService = mock(RagService.class);
        UUID jobId = UUID.randomUUID();

        IngestionRequestedEvent event = event(jobId);
        when(repository.markProcessing(jobId, event.documentId())).thenReturn(false);

        new IngestionJobListener(repository, objectStorage, ragService).consume(event);

        verify(repository).markProcessing(jobId, event.documentId());
        verify(objectStorage, org.mockito.Mockito.never()).get("documents/doc-1.md");
        verifyNoInteractions(ragService);
    }

    @Test
    void shouldMarkJobFailedAndRejectMessageWhenProcessingFails() {
        DocumentIngestionRepository repository = mock(DocumentIngestionRepository.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        RagService ragService = mock(RagService.class);
        UUID jobId = UUID.randomUUID();
        IngestionRequestedEvent event = event(jobId);

        when(repository.markProcessing(jobId, event.documentId())).thenReturn(true);
        when(objectStorage.get("documents/doc-1.md"))
                .thenReturn(new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)));
        when(repository.markFailed(jobId, event.documentId(), "INGESTION_FAILED", "embedding unavailable"))
                .thenReturn(true);
        doThrow(new IllegalStateException("embedding unavailable"))
                .when(ragService)
                .addDocument(any(), eq("md"), anyMap());

        assertThrows(IllegalStateException.class,
                () -> new IngestionJobListener(repository, objectStorage, ragService).consume(event));

        verify(repository).markFailed(
                jobId,
                event.documentId(),
                "INGESTION_FAILED",
                "embedding unavailable");
    }

    @Test
    void shouldRejectMessageWhenReadyTransitionFails() {
        DocumentIngestionRepository repository = mock(DocumentIngestionRepository.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        RagService ragService = mock(RagService.class);
        UUID jobId = UUID.randomUUID();

        IngestionRequestedEvent event = event(jobId);
        when(repository.markProcessing(jobId, event.documentId())).thenReturn(true);
        when(repository.markReady(jobId, event.documentId())).thenReturn(false);
        when(repository.markFailed(eq(jobId), eq(event.documentId()), eq("INGESTION_FAILED"), any()))
                .thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> new IngestionJobListener(repository, objectStorage, ragService).consume(event));

        verify(repository).markFailed(
                jobId,
                event.documentId(),
                "INGESTION_FAILED",
                "入库任务无法迁移到 READY: " + jobId);
    }

    private IngestionRequestedEvent event(UUID jobId) {
        return new IngestionRequestedEvent(
                jobId,
                UUID.randomUUID(),
                "upload-1",
                "documents/doc-1.md",
                "doc-1.md",
                "md");
    }
}
