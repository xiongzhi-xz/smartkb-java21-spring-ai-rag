package com.smartkb.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 向量存储服务
 * <p>
 * 核心功能：
 * 1. 封装 PgVectorStore 的 CRUD 操作
 * 2. 提供语义检索能力（相似度搜索）
 * 3. 支持 Metadata 过滤（后续 Advanced RAG 使用）
 * <p>
 * 与 Spring AI Advisor 的配合：
 * - RetrievalAugmentationAdvisor 会调用 VectorStore.search() 进行检索
 * - 本服务是 VectorStore 的业务层封装，提供更高级的操作
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreService {

    private final PgVectorStore vectorStore;

    /**
     * 添加文档到向量库（批量）
     *
     * @param documents 文档列表（已包含 Embedding 向量）
     */
    public void addDocuments(List<Document> documents) {
        if (documents.isEmpty()) {
            log.warn("文档列表为空，跳过添加操作");
            return;
        }

        log.info("添加文档到向量库: {} 个文档", documents.size());

        try {
            vectorStore.add(documents);
            log.info("文档添加成功: {} 个文档", documents.size());
        } catch (Exception e) {
            log.error("文档添加失败", e);
            throw new RuntimeException("文档添加失败: " + e.getMessage(), e);
        }
    }

    /**
     * 语义检索（基于余弦相似度）
     *
     * @param query            查询文本
     * @param topK             返回 Top-K 最相关文档
     * @param similarityThreshold 相似度阈值（0.0-1.0）
     * @return 相关文档列表（按相似度降序）
     */
    public List<Document> searchSimilarDocuments(String query, int topK, double similarityThreshold) {
        return searchSimilarDocuments(query, topK, similarityThreshold, null);
    }

    /**
     * 语义检索（支持 metadata 过滤下推）
     *
     * @param query               查询文本
     * @param topK                返回 Top-K 最相关文档
     * @param similarityThreshold 相似度阈值（0.0-1.0）
     * @param metadataFilter      元数据过滤条件
     * @return 相关文档列表（按相似度降序）
     */
    public List<Document> searchSimilarDocuments(
            String query,
            int topK,
            double similarityThreshold,
            Map<String, Object> metadataFilter) {
        log.debug("语义检索: query='{}', topK={}, threshold={}",
                query.substring(0, Math.min(50, query.length())), topK, similarityThreshold);

        try {
            SearchRequest request = SearchRequest.query(query)
                    .withTopK(topK)
                    .withSimilarityThreshold(similarityThreshold);

            Filter.Expression filterExpression = buildFilterExpression(metadataFilter);
            if (filterExpression != null) {
                request = request.withFilterExpression(filterExpression);
            }

            List<Document> results = vectorStore.similaritySearch(request);

            log.debug("检索完成: 返回 {} 个文档", results.size());
            return results;

        } catch (Exception e) {
            log.error("语义检索失败: {}", query, e);
            throw new RuntimeException("语义检索失败: " + e.getMessage(), e);
        }
    }

    private Filter.Expression buildFilterExpression(Map<String, Object> metadataFilter) {
        if (metadataFilter == null || metadataFilter.isEmpty()) {
            return null;
        }

        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op combined = null;

        for (Map.Entry<String, Object> entry : metadataFilter.entrySet()) {
            if (!hasFilterValue(entry.getValue())) {
                continue;
            }

            FilterExpressionBuilder.Op current = builder.eq(entry.getKey(), entry.getValue());
            combined = combined == null ? current : builder.and(combined, current);
        }

        return combined == null ? null : combined.build();
    }

    private boolean hasFilterValue(Object value) {
        if (value == null) {
            return false;
        }
        return !(value instanceof String text) || !text.isBlank();
    }

    /**
     * 根据文档 ID 删除（可选功能）
     *
     * @param documentId 文档 ID
     */
    public void deleteDocument(String documentId) {
        log.info("删除文档: {}", documentId);

        try {
            vectorStore.delete(List.of(documentId));
            log.info("文档删除成功: {}", documentId);
        } catch (Exception e) {
            log.error("文档删除失败: {}", documentId, e);
            throw new RuntimeException("文档删除失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量删除文档
     *
     * @param documentIds 文档 ID 列表
     */
    public void deleteDocuments(List<String> documentIds) {
        if (documentIds.isEmpty()) {
            return;
        }

        log.info("批量删除文档: {} 个", documentIds.size());

        try {
            vectorStore.delete(documentIds);
            log.info("批量删除成功: {} 个文档", documentIds.size());
        } catch (Exception e) {
            log.error("批量删除失败", e);
            throw new RuntimeException("批量删除失败: " + e.getMessage(), e);
        }
    }
}
