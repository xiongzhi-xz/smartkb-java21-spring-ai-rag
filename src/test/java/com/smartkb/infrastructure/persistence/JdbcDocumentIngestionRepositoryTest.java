package com.smartkb.infrastructure.persistence;

import com.smartkb.application.port.outbound.IngestionJobRepository;
import com.smartkb.domain.DocumentIngestionSubmission;
import com.smartkb.domain.IngestionJob;
import com.smartkb.domain.IngestionJobStatus;
import com.smartkb.domain.KnowledgeDocument;
import com.smartkb.domain.KnowledgeDocumentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcDocumentIngestionRepositoryTest {

    @Test
    void shouldPrepareDocumentAndJobUsingPersistedDocumentIdentity() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        IngestionJobRepository jobRepository = mock(IngestionJobRepository.class);
        JdbcDocumentIngestionRepository repository = new JdbcDocumentIngestionRepository(jdbcTemplate, jobRepository);
        UUID knowledgeBaseId = UUID.randomUUID();
        UUID requestedDocumentId = UUID.randomUUID();
        UUID persistedDocumentId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        KnowledgeDocument requested = document(requestedDocumentId, knowledgeBaseId);
        KnowledgeDocument persisted = document(persistedDocumentId, knowledgeBaseId);
        IngestionJob requestedJob = new IngestionJob(
                jobId, requestedDocumentId, "upload-key", IngestionJobStatus.PENDING, 0);

        when(jdbcTemplate.queryForObject(
                anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<KnowledgeDocument>>any(),
                eq(knowledgeBaseId),
                eq(requested.contentChecksum()),
                eq(1)))
                .thenReturn(persisted);
        when(jobRepository.createOrGet(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentIngestionSubmission submission = repository.prepare(
                requested,
                requestedJob,
                "Default Knowledge Base");

        assertThat(submission.document().id()).isEqualTo(persistedDocumentId);
        assertThat(submission.job().documentId()).isEqualTo(persistedDocumentId);
        verify(jobRepository).createOrGet(new IngestionJob(
                jobId, persistedDocumentId, "upload-key", IngestionJobStatus.PENDING, 0));
    }

    @Test
    void shouldTransitionJobAndDocumentToProcessingTogether() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcDocumentIngestionRepository repository = new JdbcDocumentIngestionRepository(
                jdbcTemplate,
                mock(IngestionJobRepository.class));
        UUID jobId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        when(jdbcTemplate.update(anyString(), eq(jobId), eq(documentId))).thenReturn(1);
        when(jdbcTemplate.update(anyString(), eq(documentId))).thenReturn(1);

        assertThat(repository.markProcessing(jobId, documentId)).isTrue();

        verify(jdbcTemplate).update(anyString(), eq(jobId), eq(documentId));
        verify(jdbcTemplate).update(anyString(), eq(documentId));
    }

    private KnowledgeDocument document(UUID documentId, UUID knowledgeBaseId) {
        return new KnowledgeDocument(
                documentId,
                knowledgeBaseId,
                "demo.md",
                "text/markdown",
                "documents/" + documentId + "/demo.md",
                "a".repeat(64),
                7,
                1,
                KnowledgeDocumentStatus.PENDING);
    }
}
