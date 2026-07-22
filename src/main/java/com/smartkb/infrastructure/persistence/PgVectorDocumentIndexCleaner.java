package com.smartkb.infrastructure.persistence;

import com.smartkb.application.port.outbound.DocumentIndexCleaner;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 当前 pgvector 兼容链路的企业文档索引清理适配器。 */
@Component
@RequiredArgsConstructor
public class PgVectorDocumentIndexCleaner implements DocumentIndexCleaner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public int deleteByDocumentId(UUID documentId) {
        return jdbcTemplate.update("""
                DELETE FROM vector_store
                WHERE metadata->>'documentId' = ?
                """, documentId.toString());
    }
}
