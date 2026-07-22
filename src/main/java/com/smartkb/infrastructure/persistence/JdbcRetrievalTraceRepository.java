package com.smartkb.infrastructure.persistence;

import com.smartkb.application.port.outbound.RetrievalTraceRepository;
import com.smartkb.domain.RetrievalTrace;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JdbcRetrievalTraceRepository implements RetrievalTraceRepository {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void save(UUID traceId, UUID assistantMessageId, String query, String candidatesJson,
                     String retrievalMode, long latencyMs) {
        jdbcTemplate.update("""
                INSERT INTO retrieval_trace
                    (id, conversation_message_id, query_text, candidates, rerank_mode, latency_ms, created_at)
                VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?, CURRENT_TIMESTAMP)
                """, traceId, assistantMessageId, query, candidatesJson, retrievalMode, latencyMs);
    }

    @Override
    public Optional<RetrievalTrace> findById(UUID traceId) {
        return jdbcTemplate.query("""
                SELECT id, conversation_message_id, query_text, candidates::text, rerank_mode, latency_ms, created_at
                FROM retrieval_trace WHERE id = ?
                """, (resultSet, ignored) -> new RetrievalTrace(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("conversation_message_id", UUID.class),
                resultSet.getString("query_text"), resultSet.getString("candidates"),
                resultSet.getString("rerank_mode"), resultSet.getLong("latency_ms"),
                resultSet.getTimestamp("created_at").toInstant()), traceId).stream().findFirst();
    }
}
