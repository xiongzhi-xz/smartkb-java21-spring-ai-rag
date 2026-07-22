package com.smartkb.infrastructure.persistence;

import com.smartkb.domain.DocumentIngestionSubmission;
import com.smartkb.domain.IngestionJob;
import com.smartkb.domain.IngestionJobStatus;
import com.smartkb.domain.KnowledgeDocument;
import com.smartkb.domain.KnowledgeDocumentStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证企业 RAG 文档事实、任务状态机和 Flyway 迁移可在真实 PostgreSQL 中协同运行。
 */
@Testcontainers
class EnterpriseRagPersistenceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("smartkb_it")
            .withUsername("smartkb")
            .withPassword("smartkb");

    private static JdbcTemplate jdbcTemplate;
    private static JdbcDocumentIngestionRepository ingestionRepository;
    private static JdbcDocumentDeletionRepository deletionRepository;

    @BeforeAll
    static void setUpDatabase() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);
        ingestionRepository = new JdbcDocumentIngestionRepository(
                jdbcTemplate, new JdbcIngestionJobRepository(jdbcTemplate));
        deletionRepository = new JdbcDocumentDeletionRepository(jdbcTemplate);
    }

    @AfterEach
    void clearEnterpriseRagFacts() {
        jdbcTemplate.execute("TRUNCATE TABLE knowledge_base CASCADE");
    }

    @Test
    void shouldApplyMigrationsAndReuseDocumentAndJobForDuplicateSubmission() {
        UUID knowledgeBaseId = UUID.randomUUID();
        String checksum = "a".repeat(64);
        KnowledgeDocument firstRequested = document(
                UUID.randomUUID(), knowledgeBaseId, checksum, KnowledgeDocumentStatus.PENDING);
        IngestionJob firstJob = job(UUID.randomUUID(), firstRequested.id(), "upload-1", IngestionJobStatus.PENDING, 0);

        DocumentIngestionSubmission first = ingestionRepository.prepare(
                firstRequested, firstJob, "Integration Knowledge Base");
        DocumentIngestionSubmission duplicate = ingestionRepository.prepare(
                document(UUID.randomUUID(), knowledgeBaseId, checksum, KnowledgeDocumentStatus.PENDING),
                job(UUID.randomUUID(), UUID.randomUUID(), "upload-1", IngestionJobStatus.PENDING, 0),
                "Integration Knowledge Base");

        assertThat(duplicate.document().id()).isEqualTo(first.document().id());
        assertThat(duplicate.job().id()).isEqualTo(first.job().id());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kb_document", Integer.class)).isOne();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ingestion_job", Integer.class)).isOne();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_name = 'ingestion_job' AND column_name = 'idempotency_key'
                """, Integer.class)).isOne();
    }

    @Test
    void shouldProtectDuplicateConsumptionRetryFailedJobAndDeleteOnlyStableDocument() {
        KnowledgeDocument requested = document(
                UUID.randomUUID(), UUID.randomUUID(), "b".repeat(64), KnowledgeDocumentStatus.PENDING);
        IngestionJob requestedJob = job(UUID.randomUUID(), requested.id(), "upload-2", IngestionJobStatus.PENDING, 0);
        DocumentIngestionSubmission submission = ingestionRepository.prepare(
                requested, requestedJob, "Integration Knowledge Base");

        assertThat(ingestionRepository.markProcessing(submission.job().id(), submission.document().id())).isTrue();
        assertThat(ingestionRepository.markProcessing(submission.job().id(), submission.document().id())).isFalse();
        assertThat(deletionRepository.delete(submission.document().id(), submission.job().id())).isFalse();

        assertThat(ingestionRepository.markFailed(
                submission.job().id(), submission.document().id(), "EMBEDDING_FAILED", "embedding unavailable"))
                .isTrue();
        DocumentIngestionSubmission failed = ingestionRepository.findLatest(submission.document().id()).orElseThrow();
        assertThat(failed.document().status()).isEqualTo(KnowledgeDocumentStatus.FAILED);
        assertThat(failed.job().status()).isEqualTo(IngestionJobStatus.FAILED);

        DocumentIngestionSubmission retrying = ingestionRepository
                .markRetrying(submission.job().id(), submission.document().id())
                .orElseThrow();
        assertThat(retrying.document().status()).isEqualTo(KnowledgeDocumentStatus.FAILED);
        assertThat(retrying.job().status()).isEqualTo(IngestionJobStatus.RETRYING);
        assertThat(retrying.job().retryCount()).isEqualTo(1);
        assertThat(ingestionRepository.markRetrying(submission.job().id(), submission.document().id())).isEmpty();

        assertThat(ingestionRepository.markProcessing(submission.job().id(), submission.document().id())).isTrue();
        assertThat(ingestionRepository.markReady(submission.job().id(), submission.document().id())).isTrue();
        DocumentIngestionSubmission ready = deletionRepository.lockLatest(submission.document().id()).orElseThrow();
        assertThat(ready.document().status()).isEqualTo(KnowledgeDocumentStatus.READY);
        assertThat(ready.job().status()).isEqualTo(IngestionJobStatus.READY);

        assertThat(deletionRepository.delete(ready.document().id(), ready.job().id())).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM kb_document", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ingestion_job", Integer.class)).isZero();
    }

    private static KnowledgeDocument document(
            UUID documentId,
            UUID knowledgeBaseId,
            String checksum,
            KnowledgeDocumentStatus status) {
        return new KnowledgeDocument(
                documentId,
                knowledgeBaseId,
                "integration.md",
                "text/markdown",
                "documents/" + documentId + "/integration.md",
                checksum,
                128,
                1,
                status);
    }

    private static IngestionJob job(
            UUID jobId,
            UUID documentId,
            String idempotencyKey,
            IngestionJobStatus status,
            int retryCount) {
        return new IngestionJob(jobId, documentId, idempotencyKey, status, retryCount);
    }
}
