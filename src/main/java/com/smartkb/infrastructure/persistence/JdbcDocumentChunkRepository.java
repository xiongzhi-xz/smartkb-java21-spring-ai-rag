package com.smartkb.infrastructure.persistence;

import com.smartkb.application.port.outbound.DocumentChunkRepository;
import com.smartkb.domain.IndexableChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** PostgreSQL remains the authoritative source for stable chunk identities. */
@Repository
@RequiredArgsConstructor
public class JdbcDocumentChunkRepository implements DocumentChunkRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public List<IndexableChunk> persistOrVerify(UUID documentId, UUID knowledgeBaseId, int versionNo,
                                                List<IndexableChunk> chunks) {
        for (IndexableChunk chunk : chunks) {
            jdbcTemplate.update("""
                    INSERT INTO document_chunk (id, document_id, ordinal, content_hash, index_status)
                    VALUES (?, ?, ?, ?, 'PENDING')
                    ON CONFLICT (document_id, ordinal) DO NOTHING
                    """, chunk.chunkId(), documentId, chunk.ordinal(), chunk.contentHash());
            String persistedHash = jdbcTemplate.queryForObject("""
                    SELECT content_hash FROM document_chunk WHERE document_id = ? AND ordinal = ?
                    """, String.class, documentId, chunk.ordinal());
            if (!chunk.contentHash().equals(persistedHash)) {
                throw new IllegalStateException("deterministic chunk content changed for document "
                        + documentId + " ordinal " + chunk.ordinal());
            }
        }
        return List.copyOf(chunks);
    }

    @Override
    @Transactional
    public void markReady(UUID documentId, List<UUID> chunkIds) {
        if (chunkIds.isEmpty()) {
            throw new IllegalArgumentException("indexed document must contain at least one chunk");
        }
        String ids = "{" + chunkIds.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(",")) + "}";
        int updated = jdbcTemplate.update("""
                UPDATE document_chunk SET index_status = 'READY', updated_at = CURRENT_TIMESTAMP
                WHERE document_id = ? AND id = ANY (?::uuid[]) AND index_status IN ('PENDING', 'READY')
                """, documentId, ids);
        if (updated != chunkIds.size()) {
            throw new IllegalStateException("unable to finalize all document chunks for " + documentId);
        }
    }
}
