package com.smartkb.application;

import com.smartkb.application.port.outbound.DocumentIngestionRepository;
import com.smartkb.application.port.outbound.IngestionJobPublisher;
import com.smartkb.application.port.outbound.ObjectStorage;
import com.smartkb.domain.DocumentIngestionSubmission;
import com.smartkb.domain.IngestionJob;
import com.smartkb.domain.IngestionJobStatus;
import com.smartkb.domain.IngestionRequestedEvent;
import com.smartkb.domain.KnowledgeDocument;
import com.smartkb.domain.KnowledgeDocumentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentIngestionSubmissionServiceTest {

    private static final UUID KNOWLEDGE_BASE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void shouldStoreObjectAndPublishPendingSubmission() {
        DocumentIngestionRepository repository = mock(DocumentIngestionRepository.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        IngestionJobPublisher publisher = mock(IngestionJobPublisher.class);
        byte[] content = "SmartKB content".getBytes(StandardCharsets.UTF_8);

        when(repository.prepare(any(), any(), eq("Default Knowledge Base")))
                .thenAnswer(invocation -> {
                    KnowledgeDocument document = invocation.getArgument(0);
                    IngestionJob requestedJob = invocation.getArgument(1);
                    return new DocumentIngestionSubmission(
                            document,
                            new IngestionJob(
                                    requestedJob.id(),
                                    document.id(),
                                    requestedJob.idempotencyKey(),
                                    IngestionJobStatus.PENDING,
                                    0));
                });

        DocumentUploadResult result = service(repository, objectStorage, publisher).submit(
                new ByteArrayResource(content),
                "../demo.md",
                "md",
                "text/markdown",
                content.length);

        assertThat(result.fileName()).isEqualTo("demo.md");
        assertThat(result.status()).isEqualTo(IngestionJobStatus.PENDING);
        assertThat(result.queued()).isTrue();

        var documentCaptor = org.mockito.ArgumentCaptor.forClass(KnowledgeDocument.class);
        verify(repository).prepare(documentCaptor.capture(), any(), eq("Default Knowledge Base"));
        KnowledgeDocument document = documentCaptor.getValue();
        assertThat(document.knowledgeBaseId()).isEqualTo(KNOWLEDGE_BASE_ID);
        assertThat(document.contentChecksum()).hasSize(64);
        assertThat(document.objectKey()).endsWith("/demo.md");

        verify(objectStorage).put(
                eq(document.objectKey()),
                any(),
                eq((long) content.length),
                eq("text/markdown"));
        verify(publisher).publish(any(IngestionRequestedEvent.class));
    }

    @Test
    void shouldReuseCompletedSubmissionWithoutPublishingAgain() {
        DocumentIngestionRepository repository = mock(DocumentIngestionRepository.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        IngestionJobPublisher publisher = mock(IngestionJobPublisher.class);
        UUID documentId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        KnowledgeDocument document = new KnowledgeDocument(
                documentId,
                KNOWLEDGE_BASE_ID,
                "demo.md",
                "text/markdown",
                "documents/existing/demo.md",
                "a".repeat(64),
                7,
                1,
                KnowledgeDocumentStatus.READY);
        IngestionJob job = new IngestionJob(jobId, documentId, "upload-existing", IngestionJobStatus.READY, 0);
        when(repository.prepare(any(), any(), eq("Default Knowledge Base")))
                .thenReturn(new DocumentIngestionSubmission(document, job));

        DocumentUploadResult result = service(repository, objectStorage, publisher).submit(
                new ByteArrayResource("content".getBytes(StandardCharsets.UTF_8)),
                "demo.md",
                "md",
                "text/markdown",
                7);

        assertThat(result.documentId()).isEqualTo(documentId);
        assertThat(result.jobId()).isEqualTo(jobId);
        assertThat(result.status()).isEqualTo(IngestionJobStatus.READY);
        assertThat(result.queued()).isFalse();
        verifyNoInteractions(objectStorage, publisher);
        verify(repository, never()).markProcessing(any(), any());
    }

    private DocumentIngestionSubmissionService service(
            DocumentIngestionRepository repository,
            ObjectStorage objectStorage,
            IngestionJobPublisher publisher) {
        return new DocumentIngestionSubmissionService(
                repository,
                objectStorage,
                publisher,
                KNOWLEDGE_BASE_ID.toString(),
                "Default Knowledge Base");
    }
}
