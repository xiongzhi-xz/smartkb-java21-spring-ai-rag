package com.smartkb.infrastructure.retrieval;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.smartkb.application.port.outbound.DenseVectorIndex;
import com.smartkb.domain.IndexableChunk;
import com.smartkb.domain.RetrievalCandidate;
import com.smartkb.domain.RetrievalRequest;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.UpsertParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Milvus writer. Search is added with the retrieval orchestration in Phase 3d. */
@Component
@RequiredArgsConstructor
public class MilvusDenseVectorIndex implements DenseVectorIndex {

    private final MilvusServiceClient client;
    private final RetrievalIndexProperties properties;

    @Override
    public void upsert(List<IndexableChunk> chunks) {
        if (chunks.isEmpty()) return;
        ensureCollection();
        List<JsonObject> rows = chunks.stream().map(this::row).toList();
        requireSuccess(client.upsert(UpsertParam.newBuilder()
                .withCollectionName(properties.getMilvus().getCollection()).withRows(rows).build()), "upsert");
    }

    @Override
    public List<RetrievalCandidate> search(RetrievalRequest request) {
        throw new UnsupportedOperationException("Milvus retrieval is implemented in Phase 3d");
    }

    @Override
    public int deleteByDocumentId(UUID documentId) {
        requireSuccess(client.delete(DeleteParam.newBuilder()
                .withCollectionName(properties.getMilvus().getCollection())
                .withExpr("documentId == \\\"" + documentId + "\\\"").build()), "delete");
        return 0;
    }

    private void ensureCollection() {
        String collection = properties.getMilvus().getCollection();
        R<Boolean> existing = client.hasCollection(HasCollectionParam.newBuilder().withCollectionName(collection).build());
        requireSuccess(existing, "check collection");
        if (Boolean.TRUE.equals(existing.getData())) return;
        List<FieldType> fields = List.of(
                varchar("chunkId", true), varchar("documentId", false), varchar("knowledgeBaseId", false),
                FieldType.newBuilder().withName("versionNo").withDataType(DataType.Int32).build(),
                FieldType.newBuilder().withName("ordinal").withDataType(DataType.Int32).build(),
                FieldType.newBuilder().withName("embedding").withDataType(DataType.FloatVector)
                        .withDimension(properties.getMilvus().getEmbeddingDimensions()).build());
        requireSuccess(client.createCollection(CreateCollectionParam.newBuilder()
                .withCollectionName(collection).withFieldTypes(fields).build()), "create collection");
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
        JsonArray embedding = new JsonArray();
        chunk.embedding().forEach(embedding::add);
        row.add("embedding", embedding);
        return row;
    }

    private void requireSuccess(R<?> result, String operation) {
        if (result.getException() != null) throw new IllegalStateException("Milvus " + operation + " failed", result.getException());
    }
}
