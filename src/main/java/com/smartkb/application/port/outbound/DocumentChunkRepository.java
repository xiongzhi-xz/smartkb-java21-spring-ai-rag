package com.smartkb.application.port.outbound;

import com.smartkb.domain.IndexableChunk;

import java.util.List;
import java.util.UUID;

/** Persists the authoritative identity and index state of enterprise document chunks. */
public interface DocumentChunkRepository {

    List<IndexableChunk> persistOrVerify(UUID documentId, UUID knowledgeBaseId, int versionNo,
                                         List<IndexableChunk> chunks);

    void markReady(UUID documentId, List<UUID> chunkIds);
}
