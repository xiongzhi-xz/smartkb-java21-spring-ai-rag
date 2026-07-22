package com.smartkb.application.port.outbound;

import com.smartkb.domain.IndexableChunk;
import com.smartkb.domain.RetrievalCandidate;
import com.smartkb.domain.RetrievalRequest;

import java.util.List;
import java.util.UUID;

/** Keyword index boundary; implemented by OpenSearch in Phase 3b. */
public interface KeywordIndex {

    void upsert(List<IndexableChunk> chunks);

    List<RetrievalCandidate> search(RetrievalRequest request);

    int deleteByDocumentId(UUID documentId);
}
