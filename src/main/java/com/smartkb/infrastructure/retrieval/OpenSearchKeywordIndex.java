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
import org.opensearch.client.opensearch._types.FieldValue;

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
        try {
            var response = client.search(search -> search.index(properties.getOpensearch().getIndex())
                    .size(request.candidateTopK()).query(query -> query.bool(bool -> {
                        bool.must(match -> match.match(value -> value.field("content")
                                .query(FieldValue.of(request.query()))));
                        bool.filter(filter -> filter.term(term -> term.field("knowledgeBaseId")
                                .value(FieldValue.of(request.knowledgeBaseId().toString()))));
                        if (!request.documentIds().isEmpty()) {
                            bool.filter(filter -> filter.terms(terms -> terms.field("documentId").terms(values -> values
                                    .value(request.documentIds().stream().map(id -> FieldValue.of(id.toString())).toList()))));
                        }
                        return bool;
                    })), Map.class);
            return response.hits().hits().stream().map(hit -> candidate(hit.source(), hit.score())).toList();
        } catch (IOException exception) {
            throw new IllegalStateException("OpenSearch search failed", exception);
        }
    }

    @Override
    public int deleteByDocumentId(UUID documentId) {
        try {
            String index = properties.getOpensearch().getIndex();
            if (!client.indices().exists(request -> request.index(index)).value()) return 0;
            return Math.toIntExact(client.deleteByQuery(request -> request
                    .index(index)
                    .query(query -> query.term(term -> term.field("documentId.keyword")
                            .value(FieldValue.of(documentId.toString()))))
                    .refresh(true)).deleted());
        } catch (IOException exception) {
            throw new IllegalStateException("OpenSearch delete failed", exception);
        }
    }

    private RetrievalCandidate candidate(Map<String, Object> source, Double score) {
        if (source == null || score == null) throw new IllegalStateException("OpenSearch search hit is incomplete");
        return new RetrievalCandidate(UUID.fromString((String) source.get("chunkId")),
                UUID.fromString((String) source.get("documentId")), UUID.fromString((String) source.get("knowledgeBaseId")),
                ((Number) source.get("versionNo")).intValue(), ((Number) source.get("ordinal")).intValue(),
                (String) source.get("content"), score);
    }
}
