package com.smartkb.application.port.outbound;

import com.smartkb.domain.IndexableChunk;
import com.smartkb.domain.RetrievalCandidate;
import com.smartkb.domain.RetrievalRequest;

import java.util.List;
import java.util.UUID;

/** Dense-vector index boundary; implemented by Milvus in Phase 3b. */
public interface DenseVectorIndex {

    void upsert(List<IndexableChunk> chunks);

    List<RetrievalCandidate> search(RetrievalRequest request);

    int deleteByDocumentId(UUID documentId);
}
