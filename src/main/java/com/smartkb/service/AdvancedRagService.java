package com.smartkb.service;

import com.smartkb.domain.AdvancedRagResult;
import com.smartkb.domain.ReferenceChunk;
import com.smartkb.util.VirtualThreadInspector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
public class AdvancedRagService {

    private static final int CANDIDATE_TOP_K = 12;
    private static final int KEYWORD_SEARCH_TERM_LIMIT = 24;
    private static final int FINAL_TOP_K = 5;
    private static final int HEADING_SCORE_WEIGHT = 6;
    private static final double CANDIDATE_SIMILARITY_THRESHOLD = 0.55;
    private static final double FALLBACK_SIMILARITY_THRESHOLD = 0.0;
    private static final Pattern LATIN_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9+.#_-]*");
    private static final Pattern CJK_SEQUENCE_PATTERN = Pattern.compile("\\p{IsHan}{2,}");
    private static final Set<String> CJK_STOP_WORDS = Set.of(
            "什么", "为什么", "如何", "怎么", "这个", "那个", "哪些", "是否", "可以", "系统", "问题"
    );
    private static final Set<String> DOMAIN_ANCHOR_PHRASES = Set.of(
            "查询改写", "引用片段", "元数据过滤", "重排序", "流式输出", "向量检索", "检索质量",
            "Virtual Threads", "Advanced RAG", "Hybrid Search", "Spring AI", "PostgreSQL",
            "pgvector", "Ollama", "Embedding", "可信度", "可解释性", "可追溯性"
    );

    private final QueryRewritingService queryRewritingService;
    private final VectorStoreService vectorStoreService;
    private final ChatModel chatModel;

    public AdvancedRagService(
            QueryRewritingService queryRewritingService,
            VectorStoreService vectorStoreService,
            @Qualifier("openAiChatModel") ChatModel chatModel) {
        this.queryRewritingService = queryRewritingService;
        this.vectorStoreService = vectorStoreService;
        this.chatModel = chatModel;
    }

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
     * @return Advanced RAG 查询结果
     */
    public AdvancedRagResult queryAdvancedWithDetails(String question, Map<String, Object> metadataFilter, String history) {
        log.info("=== Advanced RAG 查询开始 ===");
        log.info("原始问题: {}", question);
        VirtualThreadInspector.logThreadInfo("Advanced RAG 查询开始");

        try {
            // 1. Query Rewriting - 查询改写
            log.info("步骤 1: 查询改写");
            String rewrittenQuery = queryRewritingService.rewriteQuery(question, history);
            log.info("改写后查询: {}", rewrittenQuery);

            // 2. Hybrid Search - 向量召回 + 关键词召回，避免单一路径漏掉核心章节
            log.info("步骤 2: 混合检索");
            List<Document> retrievedDocs = retrieveCandidates(question, rewrittenQuery, metadataFilter);
            log.info("检索到 {} 条候选文档", retrievedDocs.size());

            // 3. Metadata Filtering - 过滤下推后再做一次内存校验，防止异常数据漏出
            if (metadataFilter != null && !metadataFilter.isEmpty()) {
                log.info("步骤 3: 元数据过滤校验 - {}", metadataFilter);
                retrievedDocs = filterByMetadata(retrievedDocs, metadataFilter);
                log.info("过滤后剩余 {} 条文档", retrievedDocs.size());
            }

            // 4. Re-ranking - 按关键词相关度 + 内容完整度重排序
            log.info("步骤 4: 结果重排序");
            retrievedDocs = rerank(retrievedDocs, question, rewrittenQuery).stream()
                    .limit(FINAL_TOP_K)
                    .collect(Collectors.toList());

            if (retrievedDocs.isEmpty()) {
                log.warn("Advanced RAG 未检索到相关片段: {}", question);
                return new AdvancedRagResult(
                        "未检索到与问题相关的文档片段。请确认已上传正确文档，或换一种更具体的问法。",
                        rewrittenQuery,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        0
                );
            }

            // 5. 构建上下文并生成答案
            log.info("步骤 5: LLM 生成答案");
            String context = buildContext(retrievedDocs);
            String answer = generateAnswer(question, context);
            List<String> sources = extractSources(retrievedDocs);
            List<ReferenceChunk> references = extractReferences(retrievedDocs);

            log.info("=== Advanced RAG 查询完成 ===");
            VirtualThreadInspector.logThreadInfo("Advanced RAG 查询完成");
            return new AdvancedRagResult(answer, rewrittenQuery, sources, references, retrievedDocs.size());

        } catch (Exception e) {
            log.error("Advanced RAG 查询失败", e);
            throw new RuntimeException("Advanced RAG 查询失败: " + e.getMessage(), e);
        }
    }

    /**
     * Advanced RAG 查询（兼容旧调用，只返回答案）
     */
    public String queryAdvanced(String question, Map<String, Object> metadataFilter, String history) {
        return queryAdvancedWithDetails(question, metadataFilter, history).answer();
    }

    /**
     * Advanced RAG 查询（简化版，无元数据过滤）
     */
    public String queryAdvanced(String question) {
        return queryAdvanced(question, null, null);
    }

    private List<Document> retrieveCandidates(String question, String rewrittenQuery, Map<String, Object> metadataFilter) {
        Map<String, Document> merged = searchBothQueries(
                question,
                rewrittenQuery,
                metadataFilter,
                CANDIDATE_SIMILARITY_THRESHOLD
        );
        mergeKeywordSearchResults(merged, question, rewrittenQuery, metadataFilter);

        if (merged.isEmpty()) {
            log.info("高阈值未召回文档，降低阈值重试");
            merged = searchBothQueries(
                    question,
                    rewrittenQuery,
                    metadataFilter,
                    FALLBACK_SIMILARITY_THRESHOLD
            );
            mergeKeywordSearchResults(merged, question, rewrittenQuery, metadataFilter);
        }

        return new ArrayList<>(merged.values());
    }

    private Map<String, Document> searchBothQueries(
            String question,
            String rewrittenQuery,
            Map<String, Object> metadataFilter,
            double similarityThreshold) {
        Map<String, Document> merged = new LinkedHashMap<>();

        mergeSearchResults(merged, rewrittenQuery, metadataFilter, similarityThreshold);
        if (!rewrittenQuery.equalsIgnoreCase(question)) {
            mergeSearchResults(merged, question, metadataFilter, similarityThreshold);
        }

        return merged;
    }

    private void mergeSearchResults(
            Map<String, Document> merged,
            String query,
            Map<String, Object> metadataFilter,
            double similarityThreshold) {
        List<Document> documents = vectorStoreService.searchSimilarDocuments(
                query,
                CANDIDATE_TOP_K,
                similarityThreshold,
                metadataFilter
        );

        for (Document document : documents) {
            merged.putIfAbsent(documentKey(document), document);
        }
    }

    private void mergeKeywordSearchResults(
            Map<String, Document> merged,
            String question,
            String rewrittenQuery,
            Map<String, Object> metadataFilter) {
        List<String> terms = buildKeywordSearchTerms(question, rewrittenQuery);
        if (terms.isEmpty()) {
            return;
        }

        List<Document> documents = vectorStoreService.searchKeywordDocuments(
                terms,
                CANDIDATE_TOP_K,
                metadataFilter
        );

        for (Document document : documents) {
            merged.putIfAbsent(documentKey(document), document);
        }
    }

    private List<String> buildKeywordSearchTerms(String question, String rewrittenQuery) {
        String combinedQuery = question + " " + rewrittenQuery;
        Set<String> terms = new LinkedHashSet<>();
        terms.addAll(extractAnchorKeywords(combinedQuery));
        terms.addAll(extractKeywords(combinedQuery));
        return terms.stream()
                .filter(term -> term.length() >= 2)
                .limit(KEYWORD_SEARCH_TERM_LIMIT)
                .collect(Collectors.toList());
    }

    private String documentKey(Document document) {
        if (document.getId() != null && !document.getId().isBlank()) {
            return document.getId();
        }
        return String.valueOf(document.getMetadata().get("fileName")) + ":" + document.getContent().hashCode();
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
     * 简单实现：按问题关键词命中度排序，再按内容长度补充排序
     * 生产环境可使用专门的 Re-ranking 模型（如 Cohere Rerank）
     *
     * @param documents 原始文档列表
     * @return 重排序后的文档列表
     */
    private List<Document> rerank(List<Document> documents, String originalQuery, String rewrittenQuery) {
        List<String> keywords = extractKeywords(originalQuery + " " + rewrittenQuery);
        List<String> anchors = extractAnchorKeywords(originalQuery + " " + rewrittenQuery);

        return documents.stream()
                .sorted(Comparator
                        .comparingInt((Document doc) -> relevanceScore(doc.getContent(), keywords, anchors))
                        .reversed()
                        .thenComparing(Comparator.comparingInt((Document doc) -> doc.getContent().length()).reversed()))
                .collect(Collectors.toList());
    }

    private int relevanceScore(String content, List<String> keywords, List<String> anchors) {
        if (content == null || content.isBlank() || keywords.isEmpty()) {
            return 0;
        }

        String normalizedContent = content.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String keyword : keywords) {
            int occurrences = countOccurrences(normalizedContent, keyword.toLowerCase(Locale.ROOT));
            if (occurrences > 0) {
                score += occurrences * Math.min(keyword.length(), 8);
            }
        }
        score = applyAnchorPriority(content, anchors, score);
        score += headingRelevanceScore(content, keywords) * HEADING_SCORE_WEIGHT;
        score -= questionCatalogPenalty(content);
        return score;
    }

    private int applyAnchorPriority(String content, List<String> anchors, int baseScore) {
        if (anchors.isEmpty()) {
            return baseScore;
        }

        int anchorScore = plainRelevanceScore(content, anchors);
        int headingAnchorScore = headingRelevanceScore(content, anchors);
        if (anchorScore == 0) {
            return (baseScore / 4) - 240;
        }

        return baseScore + (anchorScore * 80) + (headingAnchorScore * 120);
    }

    private int headingRelevanceScore(String content, List<String> keywords) {
        return content.lines()
                .filter(line -> line.stripLeading().startsWith("#"))
                .mapToInt(line -> plainRelevanceScore(line, keywords))
                .sum();
    }

    private int plainRelevanceScore(String content, List<String> keywords) {
        String normalizedContent = content.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String keyword : keywords) {
            int occurrences = countOccurrences(normalizedContent, keyword.toLowerCase(Locale.ROOT));
            if (occurrences > 0) {
                score += occurrences * Math.min(keyword.length(), 8);
            }
        }
        return score;
    }

    private int questionCatalogPenalty(String content) {
        int penalty = 0;
        if (content.contains("推荐测试问题")) {
            penalty += 320;
        }
        if (content.contains("可以使用以下问题测试不同能力")) {
            penalty += 180;
        }
        long questionLineCount = content.lines()
                .map(String::trim)
                .filter(line -> line.endsWith("？") || line.endsWith("?"))
                .count();
        if (questionLineCount >= 3) {
            penalty += 120;
        }
        return penalty;
    }

    private int countOccurrences(String content, String keyword) {
        int count = 0;
        int index = content.indexOf(keyword);
        while (index >= 0) {
            count++;
            index = content.indexOf(keyword, index + keyword.length());
        }
        return count;
    }

    private List<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        Set<String> keywords = new LinkedHashSet<>();
        Matcher latinMatcher = LATIN_TOKEN_PATTERN.matcher(text);
        while (latinMatcher.find()) {
            String token = latinMatcher.group().toLowerCase(Locale.ROOT);
            if (token.length() >= 2) {
                keywords.add(token);
            }
        }

        Matcher cjkMatcher = CJK_SEQUENCE_PATTERN.matcher(text);
        while (cjkMatcher.find()) {
            addCjkNgrams(cjkMatcher.group(), keywords);
        }

        return keywords.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .limit(80)
                .collect(Collectors.toList());
    }

    private List<String> extractAnchorKeywords(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        String lowerText = text.toLowerCase(Locale.ROOT);
        return DOMAIN_ANCHOR_PHRASES.stream()
                .filter(phrase -> lowerText.contains(phrase.toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparingInt(String::length).reversed())
                .collect(Collectors.toList());
    }

    private void addCjkNgrams(String text, Set<String> keywords) {
        int maxLength = Math.min(6, text.length());
        for (int length = maxLength; length >= 2; length--) {
            for (int start = 0; start <= text.length() - length; start++) {
                String keyword = text.substring(start, start + length);
                if (!CJK_STOP_WORDS.contains(keyword)) {
                    keywords.add(keyword);
                }
            }
        }
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
     * 提取命中的来源文档名，用于前端展示。
     */
    private List<String> extractSources(List<Document> documents) {
        return documents.stream()
                .map(Document::getMetadata)
                .map(metadata -> metadata.get("fileName"))
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 提取命中的引用片段，用于前端展示可解释性证据。
     */
    private List<ReferenceChunk> extractReferences(List<Document> documents) {
        return documents.stream()
                .limit(5)
                .map(doc -> new ReferenceChunk(
                        String.valueOf(doc.getMetadata().getOrDefault("fileName", "未知文档")),
                        String.valueOf(doc.getId()),
                        buildPreview(doc.getContent())
                ))
                .collect(Collectors.toList());
    }

    private String buildPreview(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String compact = content.replaceAll("\\s+", " ").trim();
        int maxLength = 260;
        if (compact.length() <= maxLength) {
            return compact;
        }
        return compact.substring(0, maxLength) + "...";
    }

    /**
     * 生成答案（直接使用 ChatModel，避免再次触发默认 RAG Advisor）
     */
    private String generateAnswer(String question, String context) {
        String prompt = String.format("""
                请只根据以下文档内容回答用户问题，不要使用文档外的通用知识补充。
                如果文档中没有相关信息，请只回答“文档中未找到相关信息”。

                文档内容：
                %s

                用户问题：%s

                请用中文回答：
                """, context, question);

        return chatModel.call(new Prompt(prompt))
                .getResult()
                .getOutput()
                .getContent();
    }
}
