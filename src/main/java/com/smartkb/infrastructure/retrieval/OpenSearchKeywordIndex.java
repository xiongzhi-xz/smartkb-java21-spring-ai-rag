package com.smartkb.infrastructure.retrieval;

import com.smartkb.application.port.outbound.KeywordIndex;
import com.smartkb.domain.IndexableChunk;
import com.smartkb.domain.RetrievalCandidate;
import com.smartkb.domain.RetrievalRequest;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** OpenSearch writer. Query composition is intentionally deferred to Phase 3d. */
@Component
@RequiredArgsConstructor
public class OpenSearchKeywordIndex implements KeywordIndex {

    private final OpenSearchClient client;
    private final RetrievalIndexProperties properties;

    @Override
    public void upsert(List<IndexableChunk> chunks) {
        try {
            for (IndexableChunk chunk : chunks) {
                client.index(request -> request.index(properties.getOpensearch().getIndex()).id(chunk.indexId())
                        .document(Map.of("chunkId", chunk.indexId(), "documentId", chunk.documentId().toString(),
                                "knowledgeBaseId", chunk.knowledgeBaseId().toString(), "versionNo", chunk.versionNo(),
                                "ordinal", chunk.ordinal(), "contentHash", chunk.contentHash(), "content", chunk.content()))
                        .refresh(Refresh.True));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("OpenSearch upsert failed", exception);
        }
    }

    @Override
    public List<RetrievalCandidate> search(RetrievalRequest request) {
        throw new UnsupportedOperationException("OpenSearch retrieval is implemented in Phase 3d");
    }

    @Override
    public int deleteByDocumentId(UUID documentId) {
        throw new UnsupportedOperationException("OpenSearch cleanup is implemented in Phase 3e");
    }
}
