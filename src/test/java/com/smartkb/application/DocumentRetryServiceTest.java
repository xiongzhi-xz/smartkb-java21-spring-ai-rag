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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentRetryServiceTest {

    @Test
    void shouldVerifyObjectTransitionFailedJobAndPublishRetryEvent() {
        DocumentIngestionRepository repository = mock(DocumentIngestionRepository.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        IngestionJobPublisher publisher = mock(IngestionJobPublisher.class);
        DocumentIngestionSubmission failed = submission(IngestionJobStatus.FAILED, 0);
        DocumentIngestionSubmission retrying = submission(IngestionJobStatus.RETRYING, 1);
        when(repository.findLatest(failed.document().id())).thenReturn(Optional.of(failed));
        when(objectStorage.get(failed.document().objectKey()))
                .thenReturn(new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)));
        when(repository.markRetrying(failed.job().id(), failed.document().id()))
                .thenReturn(Optional.of(retrying));

        DocumentRetryResult result = service(repository, objectStorage, publisher).retry(failed.document().id());

        assertThat(result.status()).isEqualTo(IngestionJobStatus.RETRYING);
        assertThat(result.retryCount()).isEqualTo(1);
        assertThat(result.queued()).isTrue();
        verify(publisher).publish(any(IngestionRequestedEvent.class));
    }

    @Test
    void shouldBeIdempotentWhenRetryAlreadyInProgress() {
        DocumentIngestionRepository repository = mock(DocumentIngestionRepository.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        IngestionJobPublisher publisher = mock(IngestionJobPublisher.class);
        DocumentIngestionSubmission retrying = submission(IngestionJobStatus.RETRYING, 1);
        when(repository.findLatest(retrying.document().id())).thenReturn(Optional.of(retrying));

        DocumentRetryResult result = service(repository, objectStorage, publisher).retry(retrying.document().id());

        assertThat(result.queued()).isFalse();
        assertThat(result.status()).isEqualTo(IngestionJobStatus.RETRYING);
        verifyNoInteractions(objectStorage, publisher);
    }

    @Test
    void shouldNotPublishWhenAnotherRequestWonRetryTransition() {
        DocumentIngestionRepository repository = mock(DocumentIngestionRepository.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        IngestionJobPublisher publisher = mock(IngestionJobPublisher.class);
        DocumentIngestionSubmission failed = submission(IngestionJobStatus.FAILED, 0);
        DocumentIngestionSubmission retrying = submission(IngestionJobStatus.RETRYING, 1);
        when(repository.findLatest(failed.document().id()))
                .thenReturn(Optional.of(failed))
                .thenReturn(Optional.of(retrying));
        when(objectStorage.get(failed.document().objectKey()))
                .thenReturn(new ByteArrayInputStream(new byte[0]));
        when(repository.markRetrying(failed.job().id(), failed.document().id()))
                .thenReturn(Optional.empty());

        DocumentRetryResult result = service(repository, objectStorage, publisher).retry(failed.document().id());

        assertThat(result.queued()).isFalse();
        verifyNoInteractions(publisher);
    }

    @Test
    void shouldRejectNonFailedDocument() {
        DocumentIngestionRepository repository = mock(DocumentIngestionRepository.class);
        DocumentIngestionSubmission ready = submission(IngestionJobStatus.READY, 0);
        when(repository.findLatest(ready.document().id())).thenReturn(Optional.of(ready));

        assertThatThrownBy(() -> service(repository, mock(ObjectStorage.class), mock(IngestionJobPublisher.class))
                .retry(ready.document().id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("当前不可重试");
    }

    @Test
    void shouldLeaveFailedJobUntouchedWhenOriginalObjectIsMissing() {
        DocumentIngestionRepository repository = mock(DocumentIngestionRepository.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        DocumentIngestionSubmission failed = submission(IngestionJobStatus.FAILED, 2);
        when(repository.findLatest(failed.document().id())).thenReturn(Optional.of(failed));
        when(objectStorage.get(failed.document().objectKey()))
                .thenThrow(new IllegalStateException("对象读取失败"));

        assertThatThrownBy(() -> service(repository, objectStorage, mock(IngestionJobPublisher.class))
                .retry(failed.document().id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("原始对象不可用");
        verify(repository, never()).markRetrying(any(), any());
    }

    @Test
    void shouldCompensateWhenRetryEventPublishFails() {
        DocumentIngestionRepository repository = mock(DocumentIngestionRepository.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        IngestionJobPublisher publisher = mock(IngestionJobPublisher.class);
        DocumentIngestionSubmission failed = submission(IngestionJobStatus.FAILED, 0);
        DocumentIngestionSubmission retrying = submission(IngestionJobStatus.RETRYING, 1);
        when(repository.findLatest(failed.document().id())).thenReturn(Optional.of(failed));
        when(objectStorage.get(failed.document().objectKey())).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(repository.markRetrying(failed.job().id(), failed.document().id())).thenReturn(Optional.of(retrying));
        doThrow(new IllegalStateException("rabbit unavailable"))
                .when(publisher).publish(any(IngestionRequestedEvent.class));

        assertThatThrownBy(() -> service(repository, objectStorage, publisher).retry(failed.document().id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重试任务发布失败");
        verify(repository).markRetryPublishFailed(
                eq(retrying.job().id()), eq(retrying.document().id()), eq("RETRY_PUBLISH_FAILED"), eq("rabbit unavailable"));
    }

    private DocumentRetryService service(
            DocumentIngestionRepository repository,
            ObjectStorage objectStorage,
            IngestionJobPublisher publisher) {
        return new DocumentRetryService(repository, objectStorage, publisher);
    }

    private DocumentIngestionSubmission submission(IngestionJobStatus status, int retryCount) {
        UUID documentId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        KnowledgeDocument document = new KnowledgeDocument(
                documentId,
                UUID.randomUUID(),
                "demo.md",
                "text/markdown",
                "documents/" + documentId + "/demo.md",
                "a".repeat(64),
                7,
                1,
                KnowledgeDocumentStatus.FAILED);
        return new DocumentIngestionSubmission(document,
                new IngestionJob(jobId, documentId, "upload-key", status, retryCount));
    }
}
