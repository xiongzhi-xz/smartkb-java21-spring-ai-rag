package com.smartkb.service;

import com.smartkb.domain.AdvancedRagResult;
import com.smartkb.domain.AdvancedRagStage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdvancedRagServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void rerankPrefersAnswerSectionOverQuestionCatalog() throws Exception {
        Document questionCatalog = new Document("""
                ## 15. 推荐测试问题

                1. Virtual Threads 为什么适合 RAG 系统？
                2. 查询改写在 Advanced RAG 中解决什么问题？
                3. 为什么引用片段能提升 RAG 系统可信度？
                """, Map.of("fileName", "advanced-rag-demo.md"));

        Document answerSection = new Document("""
                ## 7. Advanced RAG：查询改写

                查询改写是 Advanced RAG 的第一步。用户的问题往往比较口语化。
                如果直接拿这些问题做向量检索，可能无法命中正确片段。
                查询改写会结合历史上下文，把问题改写为更适合检索的表达。
                """, Map.of("fileName", "advanced-rag-demo.md"));

        List<Document> ranked = rerank(
                List.of(questionCatalog, answerSection),
                "查询改写在 Advanced RAG 中解决什么问题？",
                "Advanced RAG 查询改写用于提升检索质量"
        );

        assertTrue(ranked.get(0).getContent().contains("查询改写是 Advanced RAG 的第一步"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void retrieveCandidatesMergesKeywordSearchWithVectorSearch() throws Exception {
        VectorStoreService vectorStoreService = mock(VectorStoreService.class);
        AdvancedRagService service = new AdvancedRagService(null, vectorStoreService, null, null, null);
        Map<String, Object> metadataFilter = Map.of("fileName", "advanced-rag-demo.md");

        Document vectorHit = new Document(
                "vector-hit",
                "pgvector 可以配合 HNSW 索引提升检索性能。检索时常用参数包括 topK 和相似度阈值。",
                Map.of("fileName", "advanced-rag-demo.md")
        );
        Document keywordHit = new Document(
                "keyword-hit",
                """
                        ## 7. Advanced RAG：查询改写

                        查询改写是 Advanced RAG 的第一步。用户的问题往往比较口语化。
                        如果直接拿这些问题做向量检索，可能无法命中正确片段。
                        """,
                Map.of("fileName", "advanced-rag-demo.md")
        );

        when(vectorStoreService.searchSimilarDocuments(anyString(), eq(12), anyDouble(), eq(metadataFilter)))
                .thenReturn(List.of(vectorHit));
        when(vectorStoreService.searchKeywordDocuments(anyList(), eq(12), eq(metadataFilter)))
                .thenReturn(List.of(keywordHit));

        List<Document> candidates = retrieveCandidates(
                service,
                "查询改写在 Advanced RAG 中解决什么问题？",
                "查询改写在 Advanced RAG 中用于提升检索质量",
                metadataFilter
        );

        assertTrue(candidates.stream().anyMatch(doc -> "vector-hit".equals(doc.getId())));
        assertTrue(candidates.stream().anyMatch(doc -> "keyword-hit".equals(doc.getId())));

        ArgumentCaptor<List<String>> termsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStoreService).searchKeywordDocuments(termsCaptor.capture(), eq(12), eq(metadataFilter));
        assertTrue(termsCaptor.getValue().contains("查询改写"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void keywordSearchTermsPutDomainAnchorsBeforeGenericNgrams() throws Exception {
        AdvancedRagService service = new AdvancedRagService(null, null, null, null, null);
        Method method = AdvancedRagService.class.getDeclaredMethod(
                "buildKeywordSearchTerms",
                String.class,
                String.class
        );
        method.setAccessible(true);

        List<String> terms = (List<String>) method.invoke(
                service,
                "为什么引用片段能提升 RAG 系统可信度？",
                "引用片段如何增强 RAG 系统的可信度与可追溯性"
        );

        assertTrue(terms.contains("引用片段"));
        assertTrue(terms.contains("可信度"));
        assertTrue(terms.indexOf("引用片段") < terms.indexOf("引用片段如何"));
    }

    @Test
    void rerankPrefersAnchorSectionOverGenericRetrievalSection() throws Exception {
        Document genericRetrievalSection = new Document("""
                ## 5. Embedding 与 pgvector

                pgvector 可以配合 HNSW 索引提升检索性能。检索时常用参数包括 topK 和相似度阈值。
                topK 表示返回几个最相关片段。阈值太高可能导致找不到文档，阈值太低可能引入噪声。
                如果返回内容与问题不相关，可以提高阈值或改进切片。
                """, Map.of("fileName", "advanced-rag-demo.md"));

        Document queryRewriteSection = new Document("""
                ## 7. Advanced RAG：查询改写

                查询改写是 Advanced RAG 的第一步。用户的问题往往比较口语化。
                如果直接拿这些问题做向量检索，可能无法命中正确片段。
                查询改写会结合历史上下文，把问题改写为更适合检索的表达。
                """, Map.of("fileName", "advanced-rag-demo.md"));

        List<Document> ranked = rerank(
                List.of(genericRetrievalSection, queryRewriteSection),
                "查询改写在 Advanced RAG 中解决什么问题？",
                "查询改写在 Advanced RAG 检索增强生成中用于提升检索质量"
        );

        assertTrue(ranked.get(0).getContent().contains("查询改写是 Advanced RAG 的第一步"));
    }

    @Test
    void rerankPrefersCitationSectionOverDocumentOverview() throws Exception {
        Document documentOverview = new Document("""
                # SmartKB Advanced RAG 演示知识文档

                本文档用于 SmartKB 本地演示和回归测试。它刻意写得比普通测试文档更长，
                覆盖 Java 21 Virtual Threads、Spring AI、pgvector、Advanced RAG、
                流式输出、引用片段和排障经验，上传后应该能切分出多个 chunk。

                ## 1. 项目背景

                SmartKB 是一个面向企业知识库场景的 RAG 预研项目。
                """, Map.of("fileName", "advanced-rag-demo.md"));

        Document citationSection = new Document("""
                ## 11. 引用片段与可解释性

                RAG 系统最常见的问题是答案看起来合理，但用户不知道依据是什么。
                SmartKB 在 Advanced 模式中返回引用片段，前端在回答下方显示“查看引用片段”。
                引用片段让系统具备可解释性：用户可以检查答案是否真的来自知识库，
                也可以发现检索是否命中了错误片段。
                """, Map.of("fileName", "advanced-rag-demo.md"));

        List<Document> ranked = rerank(
                List.of(documentOverview, citationSection),
                "为什么引用片段能提升 RAG 系统可信度？",
                "在 Advanced RAG 中，引用片段如何增强回答可信度与可解释性？"
        );

        assertTrue(ranked.get(0).getContent().contains("## 11. 引用片段与可解释性"));
    }

    @Test
    void queryAdvancedReportsProgressStagesWhenNoDocumentsFound() {
        QueryRewritingService queryRewritingService = mock(QueryRewritingService.class);
        VectorStoreService vectorStoreService = mock(VectorStoreService.class);
        AdvancedRagService service = new AdvancedRagService(queryRewritingService, vectorStoreService, null, null, null);

        when(queryRewritingService.rewriteQuery(anyString(), any()))
                .thenReturn("查询改写在 Advanced RAG 中解决什么问题");
        when(vectorStoreService.searchSimilarDocuments(anyString(), anyInt(), anyDouble(), any()))
                .thenReturn(List.of());
        when(vectorStoreService.searchKeywordDocuments(anyList(), anyInt(), any()))
                .thenReturn(List.of());

        List<AdvancedRagStage> stages = new ArrayList<>();
        AdvancedRagResult result = service.queryAdvancedWithDetails(
                "查询改写在 Advanced RAG 中解决什么问题？",
                Map.of("fileName", "advanced-rag-demo.md"),
                "",
                stages::add
        );

        assertNotNull(result.metrics());
        assertTrue(result.metrics().totalMs() >= 0);
        assertTrue(result.metrics().rewriteMs() >= 0);
        assertTrue(result.metrics().retrievalMs() >= 0);
        assertTrue(stages.stream().anyMatch(stage -> "rewrite_done".equals(stage.stage())));
        assertTrue(stages.stream().anyMatch(stage -> "retrieval_done".equals(stage.stage())));
        assertTrue(stages.stream().anyMatch(stage -> "no_result".equals(stage.stage())));
        assertTrue(stages.stream().anyMatch(stage ->
                "rewrite_done".equals(stage.stage()) && hasNonNegativeNumber(stage, "durationMs")));
        assertTrue(stages.stream().anyMatch(stage ->
                "retrieval_done".equals(stage.stage()) && hasNonNegativeNumber(stage, "durationMs")));
        assertTrue(stages.stream().anyMatch(stage ->
                "no_result".equals(stage.stage()) && hasNonNegativeNumber(stage, "totalMs")));
    }

    private boolean hasNonNegativeNumber(AdvancedRagStage stage, String key) {
        Object value = stage.details().get(key);
        return value instanceof Number number && number.longValue() >= 0;
    }

    @SuppressWarnings("unchecked")
    private List<Document> rerank(List<Document> documents, String originalQuery, String rewrittenQuery) throws Exception {
        AdvancedRagService service = new AdvancedRagService(null, null, null, null, null);
        Method rerank = AdvancedRagService.class.getDeclaredMethod(
                "rerank",
                List.class,
                String.class,
                String.class
        );
        rerank.setAccessible(true);
        return (List<Document>) rerank.invoke(service, documents, originalQuery, rewrittenQuery);
    }

    @SuppressWarnings("unchecked")
    private List<Document> retrieveCandidates(
            AdvancedRagService service,
            String originalQuery,
            String rewrittenQuery,
            Map<String, Object> metadataFilter) throws Exception {
        Method method = AdvancedRagService.class.getDeclaredMethod(
                "retrieveCandidates",
                String.class,
                String.class,
                Map.class
        );
        method.setAccessible(true);
        return (List<Document>) method.invoke(service, originalQuery, rewrittenQuery, metadataFilter);
    }
}
