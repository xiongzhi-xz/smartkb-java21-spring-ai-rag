package com.smartkb.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @SuppressWarnings("unchecked")
    private List<Document> rerank(List<Document> documents, String originalQuery, String rewrittenQuery) throws Exception {
        AdvancedRagService service = new AdvancedRagService(null, null, null);
        Method rerank = AdvancedRagService.class.getDeclaredMethod(
                "rerank",
                List.class,
                String.class,
                String.class
        );
        rerank.setAccessible(true);
        return (List<Document>) rerank.invoke(service, documents, originalQuery, rewrittenQuery);
    }
}
