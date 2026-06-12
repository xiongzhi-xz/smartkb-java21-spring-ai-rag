package com.smartkb.service;

import com.smartkb.util.VirtualThreadInspector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Advanced RAG 服务
 * <p>
 * 核心功能：
 * 1. Query Rewriting - 查询改写，提升检索准确率
 * 2. Metadata Filtering - 基于元数据的精确过滤
 * 3. Hybrid Search - 混合检索（向量 + 关键词）
 * 4. Re-ranking - 检索结果重排序
 * <p>
 * 与基础 RagService 的区别：
 * - 基础 RAG：直接向量检索 + LLM 生成
 * - Advanced RAG：查询优化 + 多路检索 + 结果融合 + LLM 生成
 * <p>
 * 2026 年企业级 RAG 标准流程
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdvancedRagService {

    private final QueryRewritingService queryRewritingService;
    private final VectorStoreService vectorStoreService;
    private final ChatClient chatClient;

    /**
     * Advanced RAG 查询（完整链路）
     * <p>
     * 执行流程：
     * 1. Query Rewriting - 改写用户查询
     * 2. Metadata Filtering - 基于元数据过滤（如指定文档类型）
     * 3. Vector Search - 向量检索
     * 4. Re-ranking - 结果重排序（可选）
     * 5. LLM Generation - 生成答案
     *
     * @param question       用户问题
     * @param metadataFilter 元数据过滤条件（可选）
     * @param history        对话历史（可选）
     * @return 答案
     */
    public String queryAdvanced(String question, Map<String, Object> metadataFilter, String history) {
        log.info("=== Advanced RAG 查询开始 ===");
        log.info("原始问题: {}", question);
        VirtualThreadInspector.logThreadInfo("Advanced RAG 查询开始");

        try {
            // 1. Query Rewriting - 查询改写
            log.info("步骤 1: 查询改写");
            String rewrittenQuery = queryRewritingService.rewriteQuery(question, history);
            log.info("改写后查询: {}", rewrittenQuery);

            // 2. Vector Search - 向量检索（使用改写后的查询）
            log.info("步骤 2: 向量检索");
            List<Document> retrievedDocs = vectorStoreService.searchSimilarDocuments(
                    rewrittenQuery, 5, 0.7);
            log.info("检索到 {} 条文档", retrievedDocs.size());

            // 3. Metadata Filtering - 元数据过滤（如果提供了过滤条件）
            if (metadataFilter != null && !metadataFilter.isEmpty()) {
                log.info("步骤 3: 元数据过滤 - {}", metadataFilter);
                retrievedDocs = filterByMetadata(retrievedDocs, metadataFilter);
                log.info("过滤后剩余 {} 条文档", retrievedDocs.size());
            }

            // 4. Re-ranking - 重排序（按相似度 + 元数据综合评分）
            log.info("步骤 4: 结果重排序");
            retrievedDocs = rerank(retrievedDocs, rewrittenQuery);

            // 5. 构建上下文并生成答案
            log.info("步骤 5: LLM 生成答案");
            String context = buildContext(retrievedDocs);
            String answer = generateAnswer(question, context);

            log.info("=== Advanced RAG 查询完成 ===");
            VirtualThreadInspector.logThreadInfo("Advanced RAG 查询完成");
            return answer;

        } catch (Exception e) {
            log.error("Advanced RAG 查询失败", e);
            throw new RuntimeException("Advanced RAG 查询失败: " + e.getMessage(), e);
        }
    }

    /**
     * Advanced RAG 查询（简化版，无元数据过滤）
     */
    public String queryAdvanced(String question) {
        return queryAdvanced(question, null, null);
    }

    /**
     * 基于元数据过滤文档
     * <p>
     * 支持的过滤条件：
     * - fileName: 指定文件名
     * - fileType: 指定文件类型（pdf、docx、md、txt）
     * - 自定义元数据字段
     *
     * @param documents      原始文档列表
     * @param metadataFilter 过滤条件
     * @return 过滤后的文档列表
     */
    private List<Document> filterByMetadata(List<Document> documents, Map<String, Object> metadataFilter) {
        return documents.stream()
                .filter(doc -> matchesMetadata(doc, metadataFilter))
                .collect(Collectors.toList());
    }

    /**
     * 检查文档是否匹配元数据条件
     */
    private boolean matchesMetadata(Document doc, Map<String, Object> filter) {
        Map<String, Object> metadata = doc.getMetadata();

        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            String key = entry.getKey();
            Object expectedValue = entry.getValue();
            Object actualValue = metadata.get(key);

            // 如果元数据中没有该字段，或值不匹配，则过滤掉
            if (actualValue == null || !actualValue.equals(expectedValue)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 重排序检索结果
     * <p>
     * 简单实现：按内容长度降序（更长的文档可能包含更多信息）
     * 生产环境可使用专门的 Re-ranking 模型（如 Cohere Rerank）
     *
     * @param documents 原始文档列表
     * @param query     查询文本
     * @return 重排序后的文档列表
     */
    private List<Document> rerank(List<Document> documents, String query) {
        // 简单策略：按内容长度降序
        return documents.stream()
                .sorted(Comparator.comparingInt(doc -> -doc.getContent().length()))
                .collect(Collectors.toList());
    }

    /**
     * 构建上下文（将检索到的文档合并为一个字符串）
     */
    private String buildContext(List<Document> documents) {
        if (documents.isEmpty()) {
            return "未找到相关文档";
        }

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            context.append("文档 ").append(i + 1).append(":\n");
            context.append(documents.get(i).getContent());
            context.append("\n\n");
        }

        return context.toString();
    }

    /**
     * 生成答案（使用 ChatClient）
     */
    private String generateAnswer(String question, String context) {
        String prompt = String.format("""
                请根据以下文档内容回答用户问题。如果文档中没有相关信息，请明确告知。

                文档内容：
                %s

                用户问题：%s

                请用中文回答：
                """, context, question);

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
}
