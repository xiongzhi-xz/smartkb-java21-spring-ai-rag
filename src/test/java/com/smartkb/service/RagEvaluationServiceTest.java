package com.smartkb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartkb.domain.RagEvalCase;
import com.smartkb.domain.RagEvalRequest;
import com.smartkb.domain.ReferenceChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagEvaluationServiceTest {

    private final VectorStoreService vectorStoreService = mock(VectorStoreService.class);
    private final AdvancedRagService advancedRagService = mock(AdvancedRagService.class);
    private final RagEvaluationService service = new RagEvaluationService(
            new ObjectMapper(),
            vectorStoreService,
            advancedRagService
    );

    @Test
    void shouldLoadDefaultChineseEvalCases() {
        List<RagEvalCase> cases = service.defaultCases();

        assertThat(cases).hasSize(8);
        assertThat(cases.getFirst().question()).contains("Virtual Threads");
        assertThat(cases).allSatisfy(evalCase -> assertThat(evalCase.expectedChunkIds()).isNotEmpty());
        assertThat(cases).extracting(RagEvalCase::expectedFileName)
                .containsOnly("advanced-rag-demo.md");
    }

    @Test
    void shouldCompareBaselineAndAdvancedRetrievalHits() {
        RagEvalCase rewriteCase = new RagEvalCase(
                "RAG-E03",
                "查询改写在 Advanced RAG 中解决什么问题？",
                "advanced-rag-demo.md",
                List.of("chunk-07"),
                List.of("查询改写")
        );
        RagEvalCase vectorCase = new RagEvalCase(
                "RAG-E02",
                "SmartKB 为什么选择 PostgreSQL pgvector？",
                "advanced-rag-demo.md",
                List.of("chunk-05"),
                List.of("pgvector")
        );
        Map<String, Object> filter = Map.of("fileName", "advanced-rag-demo.md");

        when(vectorStoreService.searchSimilarDocuments(rewriteCase.question(), 5, 0.0, filter))
                .thenReturn(List.of(doc("chunk-01", "项目背景和普通介绍")));
        when(vectorStoreService.searchSimilarDocuments(vectorCase.question(), 5, 0.0, filter))
                .thenReturn(List.of(doc("chunk-05", "PostgreSQL pgvector 可以保存向量和元数据。")));
        when(advancedRagService.retrieveForEvaluation(rewriteCase.question(), filter, ""))
                .thenReturn(new AdvancedRagService.RetrievalPreview(
                        "Advanced RAG 查询改写用于提升检索质量",
                        List.of(doc("chunk-07", "查询改写会把口语化问题改写为更适合检索的表达。")),
                        List.of(ref("chunk-07", "查询改写会把口语化问题改写为更适合检索的表达。"))
                ));
        when(advancedRagService.retrieveForEvaluation(vectorCase.question(), filter, ""))
                .thenReturn(new AdvancedRagService.RetrievalPreview(
                        "PostgreSQL pgvector 向量存储选型",
                        List.of(doc("chunk-05", "PostgreSQL pgvector 可以保存向量和元数据。")),
                        List.of(ref("chunk-05", "PostgreSQL pgvector 可以保存向量和元数据。"))
                ));

        var report = service.runEvaluation(new RagEvalRequest(List.of(rewriteCase, vectorCase), null, 5));

        assertThat(report.totalCases()).isEqualTo(2);
        assertThat(report.baselineHitCount()).isEqualTo(1);
        assertThat(report.advancedHitCount()).isEqualTo(2);
        assertThat(report.citationHitCount()).isEqualTo(2);
        assertThat(report.advancedImprovementCount()).isEqualTo(1);
        assertThat(report.baselineTop1HitCount()).isEqualTo(1);
        assertThat(report.advancedTop1HitCount()).isEqualTo(2);
        assertThat(report.baselineHitRate()).isEqualTo(0.5);
        assertThat(report.advancedHitRate()).isEqualTo(1.0);
        assertThat(report.baselineRecallAtK()).isEqualTo(0.5);
        assertThat(report.advancedRecallAtK()).isEqualTo(1.0);
        assertThat(report.baselineMrr()).isEqualTo(0.5);
        assertThat(report.advancedMrr()).isEqualTo(1.0);
        assertThat(report.cases().getFirst().expectedChunkIds()).containsExactly("chunk-07");
        assertThat(report.cases().getFirst().advancedMatchedChunkIds()).containsExactly("chunk-07");
        assertThat(report.cases().getFirst().advancedFirstHitRank()).isEqualTo(1);
        assertThat(report.cases().getFirst().failureReason()).isEqualTo("通过");
        assertThat(report.cases().getFirst().rewrittenQuery()).contains("查询改写");
    }

    private Document doc(String id, String content) {
        return new Document(id, content, Map.of("fileName", "advanced-rag-demo.md"));
    }

    private ReferenceChunk ref(String id, String preview) {
        return new ReferenceChunk("advanced-rag-demo.md", id, preview);
    }
}
