package com.smartkb.infrastructure.persistence;

import com.smartkb.application.port.outbound.IngestionJobRepository;
import com.smartkb.domain.IngestionJob;
import com.smartkb.domain.IngestionJobStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** PostgreSQL 入库任务仓储，幂等键由数据库唯一索引最终保证。 */
@Repository
@RequiredArgsConstructor
public class JdbcIngestionJobRepository implements IngestionJobRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public IngestionJob createOrGet(IngestionJob job) {
        jdbcTemplate.update("""
                INSERT INTO ingestion_job (id, document_id, idempotency_key, status, retry_count, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT DO NOTHING
                """, job.id(), job.documentId(), job.idempotencyKey(), job.status().name(), job.retryCount());
        return findByIdempotencyKey(job.idempotencyKey())
                .orElseThrow(() -> new IllegalStateException("幂等任务读取失败: " + job.idempotencyKey()));
    }

    @Override
    public Optional<IngestionJob> findByIdempotencyKey(String idempotencyKey) {
        return jdbcTemplate.query("""
                SELECT id, document_id, idempotency_key, status, retry_count
                FROM ingestion_job WHERE idempotency_key = ?
                """, rs -> rs.next() ? Optional.of(new IngestionJob(
                rs.getObject("id", java.util.UUID.class),
                rs.getObject("document_id", java.util.UUID.class),
                rs.getString("idempotency_key"),
                IngestionJobStatus.valueOf(rs.getString("status")),
                rs.getInt("retry_count"))) : Optional.empty(), idempotencyKey);
    }

}
