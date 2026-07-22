package com.smartkb.application;

import com.smartkb.application.port.outbound.DocumentDeletionRepository;
import com.smartkb.application.port.outbound.DocumentIndexCleaner;
import com.smartkb.application.port.outbound.DenseVectorIndex;
import com.smartkb.application.port.outbound.KeywordIndex;
import com.smartkb.application.port.outbound.ObjectStorage;
import com.smartkb.domain.DocumentIngestionSubmission;
import com.smartkb.domain.IngestionJob;
import com.smartkb.domain.IngestionJobStatus;
import com.smartkb.domain.KnowledgeDocument;
import com.smartkb.domain.KnowledgeDocumentStatus;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentDeletionServiceTest {

    @Test
    void shouldDeleteReadyDocumentInProtectedOrder() {
        DocumentDeletionRepository repository = mock(DocumentDeletionRepository.class);
        DenseVectorIndex denseVectorIndex = mock(DenseVectorIndex.class);
        KeywordIndex keywordIndex = mock(KeywordIndex.class);
        DocumentIndexCleaner indexCleaner = mock(DocumentIndexCleaner.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        DocumentIngestionSubmission ready = submission(
                KnowledgeDocumentStatus.READY, IngestionJobStatus.READY);
        when(repository.lockLatest(ready.document().id())).thenReturn(Optional.of(ready));
        when(indexCleaner.deleteByDocumentId(ready.document().id())).thenReturn(3);
        when(repository.delete(ready.document().id(), ready.job().id())).thenReturn(true);

        DocumentDeletionResult result = service(repository, denseVectorIndex, keywordIndex, indexCleaner, objectStorage)
                .delete(ready.document().id());

        assertThat(result.deletedChunks()).isEqualTo(3);
        assertThat(result.fileName()).isEqualTo("demo.md");
        InOrder order = inOrder(repository, denseVectorIndex, keywordIndex, indexCleaner, objectStorage);
        order.verify(repository).lockLatest(ready.document().id());
        order.verify(denseVectorIndex).deleteByDocumentId(ready.document().id());
        order.verify(keywordIndex).deleteByDocumentId(ready.document().id());
        order.verify(indexCleaner).deleteByDocumentId(ready.document().id());
        order.verify(objectStorage).delete(ready.document().objectKey());
        order.verify(repository).delete(ready.document().id(), ready.job().id());
    }

    @Test
    void shouldAllowFailedDocumentDeletion() {
        DocumentDeletionRepository repository = mock(DocumentDeletionRepository.class);
        DenseVectorIndex denseVectorIndex = mock(DenseVectorIndex.class);
        KeywordIndex keywordIndex = mock(KeywordIndex.class);
        DocumentIndexCleaner indexCleaner = mock(DocumentIndexCleaner.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        DocumentIngestionSubmission failed = submission(
                KnowledgeDocumentStatus.FAILED, IngestionJobStatus.FAILED);
        when(repository.lockLatest(failed.document().id())).thenReturn(Optional.of(failed));
        when(repository.delete(failed.document().id(), failed.job().id())).thenReturn(true);

        assertThat(service(repository, denseVectorIndex, keywordIndex, indexCleaner, objectStorage).delete(failed.document().id()))
                .extracting(DocumentDeletionResult::documentId)
                .isEqualTo(failed.document().id());
    }

    @Test
    void shouldRejectActiveDocumentWithoutExternalCleanup() {
        DocumentDeletionRepository repository = mock(DocumentDeletionRepository.class);
        DenseVectorIndex denseVectorIndex = mock(DenseVectorIndex.class);
        KeywordIndex keywordIndex = mock(KeywordIndex.class);
        DocumentIndexCleaner indexCleaner = mock(DocumentIndexCleaner.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        DocumentIngestionSubmission processing = submission(
                KnowledgeDocumentStatus.PROCESSING, IngestionJobStatus.PROCESSING);
        when(repository.lockLatest(processing.document().id())).thenReturn(Optional.of(processing));

        assertThatThrownBy(() -> service(repository, denseVectorIndex, keywordIndex, indexCleaner, objectStorage)
                .delete(processing.document().id()))
                .isInstanceOf(DocumentDeletionConflictException.class)
                .hasMessageContaining("当前不可删除");
        verifyNoInteractions(denseVectorIndex, keywordIndex, indexCleaner, objectStorage);
        verify(repository, never()).delete(processing.document().id(), processing.job().id());
    }

    @Test
    void shouldStopWhenIndexCleanupFails() {
        DocumentDeletionRepository repository = mock(DocumentDeletionRepository.class);
        DenseVectorIndex denseVectorIndex = mock(DenseVectorIndex.class);
        KeywordIndex keywordIndex = mock(KeywordIndex.class);
        DocumentIndexCleaner indexCleaner = mock(DocumentIndexCleaner.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        DocumentIngestionSubmission ready = submission(
                KnowledgeDocumentStatus.READY, IngestionJobStatus.READY);
        when(repository.lockLatest(ready.document().id())).thenReturn(Optional.of(ready));
        when(indexCleaner.deleteByDocumentId(ready.document().id()))
                .thenThrow(new IllegalStateException("vector unavailable"));

        assertThatThrownBy(() -> service(repository, denseVectorIndex, keywordIndex, indexCleaner, objectStorage)
                .delete(ready.document().id()))
                .hasMessage("vector unavailable");
        verifyNoInteractions(objectStorage);
        verify(repository, never()).delete(ready.document().id(), ready.job().id());
    }

    @Test
    void shouldStopBeforeKeywordAndObjectCleanupWhenDenseDeletionFails() {
        DocumentDeletionRepository repository = mock(DocumentDeletionRepository.class);
        DenseVectorIndex denseVectorIndex = mock(DenseVectorIndex.class);
        KeywordIndex keywordIndex = mock(KeywordIndex.class);
        DocumentIndexCleaner indexCleaner = mock(DocumentIndexCleaner.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        DocumentIngestionSubmission ready = submission(
                KnowledgeDocumentStatus.READY, IngestionJobStatus.READY);
        when(repository.lockLatest(ready.document().id())).thenReturn(Optional.of(ready));
        doThrow(new IllegalStateException("milvus unavailable"))
                .when(denseVectorIndex).deleteByDocumentId(ready.document().id());

        assertThatThrownBy(() -> service(repository, denseVectorIndex, keywordIndex, indexCleaner, objectStorage)
                .delete(ready.document().id()))
                .hasMessage("milvus unavailable");
        verifyNoInteractions(keywordIndex, indexCleaner, objectStorage);
        verify(repository, never()).delete(ready.document().id(), ready.job().id());
    }

    @Test
    void shouldStopBeforeCompatibilityAndObjectCleanupWhenKeywordDeletionFails() {
        DocumentDeletionRepository repository = mock(DocumentDeletionRepository.class);
        DenseVectorIndex denseVectorIndex = mock(DenseVectorIndex.class);
        KeywordIndex keywordIndex = mock(KeywordIndex.class);
        DocumentIndexCleaner indexCleaner = mock(DocumentIndexCleaner.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        DocumentIngestionSubmission ready = submission(
                KnowledgeDocumentStatus.READY, IngestionJobStatus.READY);
        when(repository.lockLatest(ready.document().id())).thenReturn(Optional.of(ready));
        doThrow(new IllegalStateException("opensearch unavailable"))
                .when(keywordIndex).deleteByDocumentId(ready.document().id());

        assertThatThrownBy(() -> service(repository, denseVectorIndex, keywordIndex, indexCleaner, objectStorage)
                .delete(ready.document().id()))
                .hasMessage("opensearch unavailable");
        verifyNoInteractions(indexCleaner, objectStorage);
        verify(repository, never()).delete(ready.document().id(), ready.job().id());
    }

    @Test
    void shouldKeepDatabaseFactWhenObjectCleanupFails() {
        DocumentDeletionRepository repository = mock(DocumentDeletionRepository.class);
        DenseVectorIndex denseVectorIndex = mock(DenseVectorIndex.class);
        KeywordIndex keywordIndex = mock(KeywordIndex.class);
        DocumentIndexCleaner indexCleaner = mock(DocumentIndexCleaner.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        DocumentIngestionSubmission ready = submission(
                KnowledgeDocumentStatus.READY, IngestionJobStatus.READY);
        when(repository.lockLatest(ready.document().id())).thenReturn(Optional.of(ready));
        doThrow(new IllegalStateException("minio unavailable"))
                .when(objectStorage).delete(ready.document().objectKey());

        assertThatThrownBy(() -> service(repository, denseVectorIndex, keywordIndex, indexCleaner, objectStorage)
                .delete(ready.document().id()))
                .hasMessage("minio unavailable");
        verify(repository, never()).delete(ready.document().id(), ready.job().id());
    }

    @Test
    void shouldReportConflictWhenFinalGuardedDeleteFails() {
        DocumentDeletionRepository repository = mock(DocumentDeletionRepository.class);
        DenseVectorIndex denseVectorIndex = mock(DenseVectorIndex.class);
        KeywordIndex keywordIndex = mock(KeywordIndex.class);
        DocumentIndexCleaner indexCleaner = mock(DocumentIndexCleaner.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        DocumentIngestionSubmission ready = submission(
                KnowledgeDocumentStatus.READY, IngestionJobStatus.READY);
        when(repository.lockLatest(ready.document().id())).thenReturn(Optional.of(ready));
        when(repository.delete(ready.document().id(), ready.job().id())).thenReturn(false);

        assertThatThrownBy(() -> service(repository, denseVectorIndex, keywordIndex, indexCleaner, objectStorage)
                .delete(ready.document().id()))
                .isInstanceOf(DocumentDeletionConflictException.class)
                .hasMessageContaining("状态保护失效");
    }

    private DocumentDeletionService service(
            DocumentDeletionRepository repository,
            DenseVectorIndex denseVectorIndex,
            KeywordIndex keywordIndex,
            DocumentIndexCleaner indexCleaner,
            ObjectStorage objectStorage) {
        return new DocumentDeletionService(repository, denseVectorIndex, keywordIndex, indexCleaner, objectStorage);
    }

    private DocumentIngestionSubmission submission(
            KnowledgeDocumentStatus documentStatus,
            IngestionJobStatus jobStatus) {
        UUID documentId = UUID.randomUUID();
        KnowledgeDocument document = new KnowledgeDocument(
                documentId,
                UUID.randomUUID(),
                "demo.md",
                "text/markdown",
                "documents/" + documentId + "/demo.md",
                "a".repeat(64),
                7,
                1,
                documentStatus);
        IngestionJob job = new IngestionJob(
                UUID.randomUUID(), documentId, "upload-key", jobStatus, 0);
        return new DocumentIngestionSubmission(document, job);
    }
}
