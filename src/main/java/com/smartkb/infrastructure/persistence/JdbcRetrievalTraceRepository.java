package com.smartkb.infrastructure.persistence;

import com.smartkb.application.port.outbound.RetrievalTraceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

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
}
