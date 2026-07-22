package com.smartkb.infrastructure.persistence;

import com.smartkb.domain.DocumentIngestionSubmission;
import com.smartkb.domain.IngestionJob;
import com.smartkb.domain.IngestionJobStatus;
import com.smartkb.domain.KnowledgeDocument;
import com.smartkb.domain.KnowledgeDocumentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcDocumentDeletionRepositoryTest {

    @Test
    void shouldLockDocumentAndLatestJobForDeletion() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcDocumentDeletionRepository repository = new JdbcDocumentDeletionRepository(jdbcTemplate);
        DocumentIngestionSubmission submission = submission();
        when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<ResultSetExtractor<Optional<DocumentIngestionSubmission>>>any(),
                eq(submission.document().id())))
                .thenReturn(Optional.of(submission));

        assertThat(repository.lockLatest(submission.document().id())).contains(submission);

        var sql = forClass(String.class);
        verify(jdbcTemplate).query(
                sql.capture(),
                org.mockito.ArgumentMatchers.<ResultSetExtractor<Optional<DocumentIngestionSubmission>>>any(),
                eq(submission.document().id()));
        assertThat(sql.getValue()).contains("FOR UPDATE OF d, j");
    }

    @Test
    void shouldDeleteOnlyStableDocumentAndMatchingJob() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcDocumentDeletionRepository repository = new JdbcDocumentDeletionRepository(jdbcTemplate);
        UUID documentId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(jdbcTemplate.update(anyString(), eq(documentId), eq(jobId))).thenReturn(1);

        assertThat(repository.delete(documentId, jobId)).isTrue();

        var sql = forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), eq(documentId), eq(jobId));
        assertThat(sql.getValue())
                .contains("d.status IN ('READY', 'FAILED')")
                .contains("j.status = d.status");
    }

    private DocumentIngestionSubmission submission() {
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
                KnowledgeDocumentStatus.READY);
        return new DocumentIngestionSubmission(document, new IngestionJob(
                UUID.randomUUID(), documentId, "upload-key", IngestionJobStatus.READY, 0));
    }
}
