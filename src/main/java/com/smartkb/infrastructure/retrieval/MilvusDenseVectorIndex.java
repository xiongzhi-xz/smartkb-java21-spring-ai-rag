package com.smartkb.infrastructure.retrieval;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.smartkb.application.port.outbound.DenseVectorIndex;
import com.smartkb.domain.IndexableChunk;
import com.smartkb.domain.RetrievalCandidate;
import com.smartkb.domain.RetrievalRequest;
import com.smartkb.service.EmbeddingService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.FlushParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.UpsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.UUID;

/** Milvus writer. Search is added with the retrieval orchestration in Phase 3d. */
@Component
@RequiredArgsConstructor
public class MilvusDenseVectorIndex implements DenseVectorIndex {

    private final MilvusServiceClient client;
    private final RetrievalIndexProperties properties;
    private final EmbeddingService embeddingService;

    @Override
    public void upsert(List<IndexableChunk> chunks) {
        if (chunks.isEmpty()) return;
        ensureCollection();
        List<JsonObject> rows = chunks.stream().map(this::row).toList();
        requireSuccess(client.upsert(UpsertParam.newBuilder()
                .withCollectionName(properties.getMilvus().getCollection()).withRows(rows).build()), "upsert");
        // Make the write visible to the following read in the same ingestion cycle.
        requireSuccess(client.flush(FlushParam.newBuilder()
                .addCollectionName(properties.getMilvus().getCollection())
                .withSyncFlush(true).build()), "flush");
    }

    @Override
    public List<RetrievalCandidate> search(RetrievalRequest request) {
        List<Float> queryVector = embeddingService.embedText(request.query()).stream().map(Double::floatValue).toList();
        R<io.milvus.grpc.SearchResults> result = client.search(SearchParam.newBuilder()
                .withCollectionName(properties.getMilvus().getCollection())
                .withMetricType(MetricType.COSINE)
                .withVectorFieldName("embedding")
                .withFloatVectors(List.of(queryVector))
                .withTopK(request.candidateTopK())
                .withExpr(filterExpression(request))
                .withOutFields(List.of("chunkId", "documentId", "knowledgeBaseId", "versionNo", "ordinal", "content"))
                .withParams("{\"nprobe\": 16}").build());
        requireSuccess(result, "search");
        try {
            SearchResultsWrapper wrapper = new SearchResultsWrapper(result.getData().getResults());
            return wrapper.getIDScore(0).stream().map(item -> new RetrievalCandidate(
                    UUID.fromString(item.getStrID()),
                    UUID.fromString((String) item.get("documentId")),
                    UUID.fromString((String) item.get("knowledgeBaseId")),
                    ((Number) item.get("versionNo")).intValue(),
                    ((Number) item.get("ordinal")).intValue(),
                    (String) item.get("content"), item.getScore())).toList();
        } catch (Exception exception) {
            throw new IllegalStateException("Milvus search response is invalid", exception);
        }
    }

    @Override
    public int deleteByDocumentId(UUID documentId) {
        String collection = properties.getMilvus().getCollection();
        R<Boolean> existing = client.hasCollection(HasCollectionParam.newBuilder().withCollectionName(collection).build());
        requireSuccess(existing, "check collection");
        if (!Boolean.TRUE.equals(existing.getData())) return 0;
        requireSuccess(client.delete(DeleteParam.newBuilder()
                .withCollectionName(collection)
                .withExpr("documentId == \"" + documentId + "\"").build()), "delete");
        return 0;
    }

    private void ensureCollection() {
        String collection = properties.getMilvus().getCollection();
        R<Boolean> existing = client.hasCollection(HasCollectionParam.newBuilder().withCollectionName(collection).build());
        requireSuccess(existing, "check collection");
        if (!Boolean.TRUE.equals(existing.getData())) {
            List<FieldType> fields = List.of(
                    varchar("chunkId", true), varchar("documentId", false), varchar("knowledgeBaseId", false),
                    FieldType.newBuilder().withName("versionNo").withDataType(DataType.Int32).build(),
                    FieldType.newBuilder().withName("ordinal").withDataType(DataType.Int32).build(),
                    FieldType.newBuilder().withName("content").withDataType(DataType.VarChar).withMaxLength(65535).build(),
                    FieldType.newBuilder().withName("embedding").withDataType(DataType.FloatVector)
                            .withDimension(properties.getMilvus().getEmbeddingDimensions()).build());
            requireSuccess(client.createCollection(CreateCollectionParam.newBuilder()
                    .withCollectionName(collection).withFieldTypes(fields).build()), "create collection");
            requireSuccess(client.createIndex(CreateIndexParam.newBuilder()
                    .withCollectionName(collection)
                    .withFieldName("embedding")
                    .withIndexType(IndexType.AUTOINDEX)
                    .withMetricType(MetricType.COSINE)
                    .withSyncMode(true)
                    .build()), "create vector index");
        }
        requireSuccess(client.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(collection).build()), "load collection");
    }

    private FieldType varchar(String name, boolean primaryKey) {
        return FieldType.newBuilder().withName(name).withDataType(DataType.VarChar)
                .withMaxLength(64).withPrimaryKey(primaryKey).withAutoID(false).build();
    }

    private JsonObject row(IndexableChunk chunk) {
        JsonObject row = new JsonObject();
        row.addProperty("chunkId", chunk.indexId());
        row.addProperty("documentId", chunk.documentId().toString());
        row.addProperty("knowledgeBaseId", chunk.knowledgeBaseId().toString());
        row.addProperty("versionNo", chunk.versionNo());
        row.addProperty("ordinal", chunk.ordinal());
        row.addProperty("content", chunk.content());
        JsonArray embedding = new JsonArray();
        chunk.embedding().forEach(embedding::add);
        row.add("embedding", embedding);
        return row;
    }

    private void requireSuccess(R<?> result, String operation) {
        if (result.getException() != null) throw new IllegalStateException("Milvus " + operation + " failed", result.getException());
    }

    private String filterExpression(RetrievalRequest request) {
        String knowledgeBase = "knowledgeBaseId == \"" + request.knowledgeBaseId() + "\"";
        if (request.documentIds().isEmpty()) return knowledgeBase;
        String documentIds = request.documentIds().stream().map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(", "));
        return knowledgeBase + " && documentId in [" + documentIds + "]";
    }
}
