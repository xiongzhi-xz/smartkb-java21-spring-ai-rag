package com.smartkb.infrastructure.persistence;

import com.smartkb.application.port.outbound.DocumentDeletionRepository;
import com.smartkb.domain.DocumentIngestionSubmission;
import com.smartkb.domain.IngestionJob;
import com.smartkb.domain.IngestionJobStatus;
import com.smartkb.domain.KnowledgeDocument;
import com.smartkb.domain.KnowledgeDocumentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** PostgreSQL 企业文档删除仓储。 */
@Repository
@RequiredArgsConstructor
public class JdbcDocumentDeletionRepository implements DocumentDeletionRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<DocumentIngestionSubmission> lockLatest(UUID documentId) {
        return jdbcTemplate.query("""
                SELECT d.id, d.knowledge_base_id, d.file_name, d.content_type, d.object_key,
                       d.content_checksum, d.size_bytes, d.version_no, d.status AS document_status,
                       j.id AS job_id, j.idempotency_key, j.status AS job_status, j.retry_count
                FROM kb_document d
                JOIN ingestion_job j ON j.id = (
                    SELECT latest.id
                    FROM ingestion_job latest
                    WHERE latest.document_id = d.id
                    ORDER BY latest.created_at DESC
                    LIMIT 1
                )
                WHERE d.id = ?
                FOR UPDATE OF d, j
                """, rs -> rs.next()
                ? Optional.of(new DocumentIngestionSubmission(
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
                                rs.getInt("retry_count"))))
                : Optional.empty(), documentId);
    }

    @Override
    public boolean delete(UUID documentId, UUID jobId) {
        return jdbcTemplate.update("""
                DELETE FROM kb_document d
                WHERE d.id = ?
                  AND d.status IN ('READY', 'FAILED')
                  AND EXISTS (
                      SELECT 1
                      FROM ingestion_job j
                      WHERE j.id = ?
                        AND j.document_id = d.id
                        AND j.status = d.status
                        AND j.status IN ('READY', 'FAILED')
                  )
                """, documentId, jobId) == 1;
    }
}
