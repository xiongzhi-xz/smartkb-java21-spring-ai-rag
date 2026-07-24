package com.smartkb.application;

import com.smartkb.application.port.outbound.DenseVectorIndex;
import com.smartkb.application.port.outbound.DocumentChunkRepository;
import com.smartkb.application.port.outbound.KeywordIndex;
import com.smartkb.domain.IndexableChunk;
import com.smartkb.domain.KnowledgeDocument;
import com.smartkb.service.DocumentLoaderService;
import com.smartkb.service.EmbeddingService;
import com.smartkb.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

/** Coordinates deterministic enterprise chunk indexing without using the legacy pgvector path. */
@Service
@RequiredArgsConstructor
public class DocumentIndexingService {

    private final DocumentLoaderService documentLoaderService;
    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository documentChunkRepository;
    private final DenseVectorIndex denseVectorIndex;
    private final KeywordIndex keywordIndex;
    private final VectorStoreService vectorStoreService;

    public void index(KnowledgeDocument document, Resource resource, String fileType) {
        List<Document> parsed = documentLoaderService.loadAndSplitDocument(resource, fileType);
        embeddingService.embedDocumentsBatch(parsed);
        List<IndexableChunk> chunks = IntStream.range(0, parsed.size())
                .mapToObj(index -> toChunk(document, index, parsed.get(index)))
                .toList();
        List<IndexableChunk> persisted;
        try {
            persisted = documentChunkRepository.persistOrVerify(
                    document.id(), document.knowledgeBaseId(), document.versionNo(), chunks);
        } catch (RuntimeException exception) {
            throw new IndexingFailureException("INDEX_FINALIZATION_FAILED", exception);
        }
        try {
            denseVectorIndex.upsert(persisted);
        } catch (RuntimeException exception) {
            throw new IndexingFailureException("MILVUS_INDEX_FAILED", exception);
        }
        try {
            keywordIndex.upsert(persisted);
        } catch (RuntimeException exception) {
            throw new IndexingFailureException("OPENSEARCH_INDEX_FAILED", exception);
        }
        try {
            vectorStoreService.addDocuments(legacyDocuments(document, fileType, parsed, persisted));
        } catch (RuntimeException exception) {
            throw new IndexingFailureException("LEGACY_PGVECTOR_INDEX_FAILED", exception);
        }
        try {
            documentChunkRepository.markReady(document.id(), persisted.stream().map(IndexableChunk::chunkId).toList());
        } catch (RuntimeException exception) {
            throw new IndexingFailureException("INDEX_FINALIZATION_FAILED", exception);
        }
    }

    private IndexableChunk toChunk(KnowledgeDocument document, int ordinal, Document parsed) {
        String content = parsed.getContent();
        UUID chunkId = UUID.nameUUIDFromBytes((document.id() + ":" + ordinal).getBytes(StandardCharsets.UTF_8));
        return new IndexableChunk(chunkId, document.id(), document.knowledgeBaseId(), document.versionNo(), ordinal,
                sha256(content), content, parsed.getEmbedding().stream().map(Double::floatValue).toList());
    }

    private List<Document> legacyDocuments(
            KnowledgeDocument document,
            String fileType,
            List<Document> parsed,
            List<IndexableChunk> persisted) {
        return IntStream.range(0, parsed.size())
                .mapToObj(index -> {
                    Document source = parsed.get(index);
                    Map<String, Object> metadata = new HashMap<>(source.getMetadata());
                    metadata.put("documentId", document.id().toString());
                    metadata.put("fileName", document.fileName());
                    metadata.put("fileType", fileType);
                    metadata.put("chunkIndex", index + 1);
                    metadata.put("evalChunkId", String.format("chunk-%02d", index + 1));
                    Document legacy = new Document(persisted.get(index).chunkId().toString(), source.getContent(), metadata);
                    legacy.setEmbedding(source.getEmbedding());
                    return legacy;
                })
                .toList();
    }

    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(64);
            for (byte item : digest) value.append(String.format("%02x", item));
            return value.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
