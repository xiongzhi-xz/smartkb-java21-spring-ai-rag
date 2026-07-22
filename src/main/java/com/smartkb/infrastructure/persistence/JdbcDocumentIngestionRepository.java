package com.smartkb.infrastructure.persistence;

import com.smartkb.application.port.outbound.DocumentIngestionRepository;
import com.smartkb.application.port.outbound.IngestionJobRepository;
import com.smartkb.domain.DocumentIngestionSubmission;
import com.smartkb.domain.IngestionJob;
import com.smartkb.domain.IngestionJobStatus;
import com.smartkb.domain.KnowledgeDocument;
import com.smartkb.domain.KnowledgeDocumentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.Optional;

/** PostgreSQL 文档事实与入库任务联合仓储。 */
@Repository
@RequiredArgsConstructor
public class JdbcDocumentIngestionRepository implements DocumentIngestionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final IngestionJobRepository ingestionJobRepository;

    @Override
    @Transactional
    public DocumentIngestionSubmission prepare(
            KnowledgeDocument requestedDocument,
            IngestionJob requestedJob,
            String knowledgeBaseName) {
        jdbcTemplate.update("""
                INSERT INTO knowledge_base (id, name, status, created_at, updated_at)
                VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO NOTHING
                """, requestedDocument.knowledgeBaseId(), knowledgeBaseName);

        jdbcTemplate.update("""
                INSERT INTO kb_document (
                    id, knowledge_base_id, file_name, content_type, object_key,
                    content_checksum, size_bytes, version_no, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (knowledge_base_id, content_checksum, version_no) DO NOTHING
                """,
                requestedDocument.id(),
                requestedDocument.knowledgeBaseId(),
                requestedDocument.fileName(),
                requestedDocument.contentType(),
                requestedDocument.objectKey(),
                requestedDocument.contentChecksum(),
                requestedDocument.sizeBytes(),
                requestedDocument.versionNo(),
                requestedDocument.status().name());

        KnowledgeDocument document = jdbcTemplate.queryForObject("""
                SELECT id, knowledge_base_id, file_name, content_type, object_key,
                       content_checksum, size_bytes, version_no, status
                FROM kb_document
                WHERE knowledge_base_id = ? AND content_checksum = ? AND version_no = ?
                """, (rs, rowNum) -> new KnowledgeDocument(
                rs.getObject("id", UUID.class),
                rs.getObject("knowledge_base_id", UUID.class),
                rs.getString("file_name"),
                rs.getString("content_type"),
                rs.getString("object_key"),
                rs.getString("content_checksum"),
                rs.getLong("size_bytes"),
                rs.getInt("version_no"),
                KnowledgeDocumentStatus.valueOf(rs.getString("status"))),
                requestedDocument.knowledgeBaseId(),
                requestedDocument.contentChecksum(),
                requestedDocument.versionNo());

        if (document == null) {
            throw new IllegalStateException("文档事实创建后读取失败: " + requestedDocument.contentChecksum());
        }

        IngestionJob job = ingestionJobRepository.createOrGet(new IngestionJob(
                requestedJob.id(),
                document.id(),
                requestedJob.idempotencyKey(),
                requestedJob.status(),
                requestedJob.retryCount()));
        if (!job.documentId().equals(document.id())) {
            throw new IllegalStateException("幂等任务关联了不同文档: " + job.id());
        }
        return new DocumentIngestionSubmission(document, job);
    }

    @Override
    public Optional<DocumentIngestionSubmission> findLatest(UUID documentId) {
        return jdbcTemplate.query("""
                SELECT d.id, d.knowledge_base_id, d.file_name, d.content_type, d.object_key,
                       d.content_checksum, d.size_bytes, d.version_no, d.status AS document_status,
                       j.id AS job_id, j.idempotency_key, j.status AS job_status, j.retry_count
                FROM kb_document d
                LEFT JOIN LATERAL (
                    SELECT id, idempotency_key, status, retry_count, created_at
                    FROM ingestion_job
                    WHERE document_id = d.id
                    ORDER BY created_at DESC
                    LIMIT 1
                ) j ON TRUE
                WHERE d.id = ?
                """, rs -> {
            if (!rs.next() || rs.getObject("job_id") == null) {
                return Optional.empty();
            }
            return Optional.of(new DocumentIngestionSubmission(
                        new KnowledgeDocument(
                                rs.getObject("id", UUID.class),
                                rs.getObject("knowledge_base_id", UUID.class),
                                rs.getString("file_name"),
                                rs.getString("content_type"),
                                rs.getString("object_key"),
                                rs.getString("content_checksum"),
                                rs.getLong("size_bytes"),
                                rs.getInt("version_no"),
                                KnowledgeDocumentStatus.valueOf(rs.getString("document_status"))),
                        new IngestionJob(
                                rs.getObject("job_id", UUID.class),
                                rs.getObject("id", UUID.class),
                                rs.getString("idempotency_key"),
                                IngestionJobStatus.valueOf(rs.getString("job_status")),
                                rs.getInt("retry_count"))));
        }, documentId);
    }

    @Override
    @Transactional
    public Optional<DocumentIngestionSubmission> markRetrying(UUID jobId, UUID documentId) {
        int jobUpdated = jdbcTemplate.update("""
                UPDATE ingestion_job
                SET status = 'RETRYING', retry_count = retry_count + 1,
                    error_code = NULL, error_message = NULL,
                    started_at = NULL, finished_at = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND document_id = ? AND status = 'FAILED'
                """, jobId, documentId);
        if (jobUpdated == 0) {
            return Optional.empty();
        }

        Integer documentExists = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM kb_document
                WHERE id = ? AND status = 'FAILED'
                """, Integer.class, documentId);
        if (documentExists == null || documentExists != 1) {
            throw new IllegalStateException("文档无法保持 FAILED 状态以重试: " + documentId);
        }
        return findLatest(documentId);
    }

    @Override
    @Transactional
    public boolean markRetryPublishFailed(
            UUID jobId,
            UUID documentId,
            String errorCode,
            String errorMessage) {
        int jobUpdated = jdbcTemplate.update("""
                UPDATE ingestion_job
                SET status = 'FAILED', error_code = ?, error_message = ?,
                    finished_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND document_id = ? AND status = 'RETRYING'
                """, errorCode, errorMessage, jobId, documentId);
        if (jobUpdated == 0) {
            return false;
        }
        Integer documentExists = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM kb_document
                WHERE id = ? AND status = 'FAILED'
                """, Integer.class, documentId);
        if (documentExists == null || documentExists != 1) {
            throw new IllegalStateException("文档无法恢复 FAILED 状态: " + documentId);
        }
        return true;
    }

    @Override
    @Transactional
    public boolean markProcessing(UUID jobId, UUID documentId) {
        int jobUpdated = jdbcTemplate.update("""
                UPDATE ingestion_job
                SET status = 'PROCESSING', started_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND document_id = ? AND status IN ('PENDING', 'RETRYING')
                """, jobId, documentId);
        if (jobUpdated == 0) {
            return false;
        }

        int documentUpdated = jdbcTemplate.update("""
                UPDATE kb_document
                SET status = 'PROCESSING', updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status IN ('PENDING', 'FAILED')
                """, documentId);
        requireSingleDocumentTransition(documentUpdated, documentId, KnowledgeDocumentStatus.PROCESSING);
        return true;
    }

    @Override
    @Transactional
    public boolean markReady(UUID jobId, UUID documentId) {
        int jobUpdated = jdbcTemplate.update("""
                UPDATE ingestion_job
                SET status = 'READY', finished_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND document_id = ? AND status = 'PROCESSING'
                """, jobId, documentId);
        if (jobUpdated == 0) {
            return false;
        }

        int documentUpdated = jdbcTemplate.update("""
                UPDATE kb_document
                SET status = 'READY', updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'PROCESSING'
                """, documentId);
        requireSingleDocumentTransition(documentUpdated, documentId, KnowledgeDocumentStatus.READY);
        return true;
    }

    @Override
    @Transactional
    public boolean markFailed(
            UUID jobId,
            UUID documentId,
            String errorCode,
            String errorMessage) {
        int jobUpdated = jdbcTemplate.update("""
                UPDATE ingestion_job
                SET status = 'FAILED', error_code = ?, error_message = ?,
                    finished_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND document_id = ? AND status = 'PROCESSING'
                """, errorCode, errorMessage, jobId, documentId);
        if (jobUpdated == 0) {
            return false;
        }

        int documentUpdated = jdbcTemplate.update("""
                UPDATE kb_document
                SET status = 'FAILED', updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'PROCESSING'
                """, documentId);
        requireSingleDocumentTransition(documentUpdated, documentId, KnowledgeDocumentStatus.FAILED);
        return true;
    }

    private void requireSingleDocumentTransition(
            int updatedRows,
            UUID documentId,
            KnowledgeDocumentStatus targetStatus) {
        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "文档无法迁移到 " + targetStatus + ": " + documentId);
        }
    }
}
