package com.smartkb.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartkb.domain.RagEvalCase;
import com.smartkb.domain.RagEvalCaseResult;
import com.smartkb.domain.RagEvalReport;
import com.smartkb.domain.RagEvalRequest;
import com.smartkb.domain.ReferenceChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * RAG 质量评测服务。
 * <p>
 * 第一版聚焦检索质量：对比普通向量召回和 Advanced RAG 检索链路的命中情况，
 * 不把 LLM 生成文本纳入评分，避免评测结果受模型输出随机性影响。
 */
@Service
@RequiredArgsConstructor
public class RagEvaluationService {

    private static final String DEFAULT_CASES_RESOURCE = "rag-eval-cases.json";
    private static final int DEFAULT_TOP_K = 5;

    private final ObjectMapper objectMapper;
    private final VectorStoreService vectorStoreService;
    private final AdvancedRagService advancedRagService;

    public List<RagEvalCase> defaultCases() {
        try {
            ClassPathResource resource = new ClassPathResource(DEFAULT_CASES_RESOURCE);
            try (InputStream inputStream = resource.getInputStream()) {
                return objectMapper.readValue(inputStream, new TypeReference<>() {
                });
            }
        } catch (IOException e) {
            throw new IllegalStateException("加载默认 RAG 评测集失败: " + DEFAULT_CASES_RESOURCE, e);
        }
    }

    public RagEvalReport runDefaultEvaluation() {
        return runEvaluation(new RagEvalRequest(defaultCases(), null, DEFAULT_TOP_K));
    }

    public RagEvalReport runEvaluation(RagEvalRequest request) {
        RagEvalRequest safeRequest = request == null
                ? new RagEvalRequest(defaultCases(), null, DEFAULT_TOP_K)
                : request;
        List<RagEvalCase> cases = safeRequest.cases() == null || safeRequest.cases().isEmpty()
                ? defaultCases()
                : safeRequest.cases();
        int topK = safeTopK(safeRequest.topK());

        List<RagEvalCaseResult> results = cases.stream()
                .filter(Objects::nonNull)
                .map(evalCase -> evaluateCase(evalCase, safeRequest.fileName(), topK))
                .toList();

        int total = results.size();
        int baselineHits = count(results, RagEvalCaseResult::baselineHit);
        int advancedHits = count(results, RagEvalCaseResult::advancedHit);
        int citationHits = count(results, RagEvalCaseResult::citationHit);
        int improvements = count(results, result -> result.advancedHit() && !result.baselineHit());

        return new RagEvalReport(
                total,
                baselineHits,
                advancedHits,
                citationHits,
                improvements,
                rate(baselineHits, total),
                rate(advancedHits, total),
                rate(citationHits, total),
                results
        );
    }

    private RagEvalCaseResult evaluateCase(RagEvalCase evalCase, String requestFileName, int topK) {
        Map<String, Object> metadataFilter = metadataFilter(evalCase, requestFileName);
        List<Document> baselineDocs = vectorStoreService.searchSimilarDocuments(
                evalCase.question(),
                topK,
                0.0,
                metadataFilter
        );
        AdvancedRagService.RetrievalPreview advanced = advancedRagService.retrieveForEvaluation(
                evalCase.question(),
                metadataFilter,
                ""
        );

        List<String> baselineMatched = matchedDocumentKeywords(baselineDocs, evalCase.expectedKeywords());
        List<String> advancedMatched = matchedDocumentKeywords(advanced.documents(), evalCase.expectedKeywords());
        boolean citationHit = !matchedReferenceKeywords(advanced.references(), evalCase.expectedKeywords()).isEmpty();

        return new RagEvalCaseResult(
                evalCase.id(),
                evalCase.question(),
                evalCase.expectedFileName(),
                safeKeywords(evalCase.expectedKeywords()),
                !baselineMatched.isEmpty(),
                !advancedMatched.isEmpty(),
                citationHit,
                baselineDocs.size(),
                advanced.documents().size(),
                advanced.rewrittenQuery(),
                baselineMatched,
                advancedMatched,
                advanced.references()
        );
    }

    private Map<String, Object> metadataFilter(RagEvalCase evalCase, String requestFileName) {
        String fileName = hasText(requestFileName) ? requestFileName : evalCase.expectedFileName();
        if (!hasText(fileName)) {
            return Collections.emptyMap();
        }
        return Map.of("fileName", fileName);
    }

    private List<String> matchedDocumentKeywords(List<Document> documents, List<String> expectedKeywords) {
        String combined = documents.stream()
                .map(Document::getContent)
                .filter(Objects::nonNull)
                .reduce("", (left, right) -> left + "\n" + right);
        return matchedKeywords(combined, expectedKeywords);
    }

    private List<String> matchedReferenceKeywords(List<ReferenceChunk> references, List<String> expectedKeywords) {
        String combined = references.stream()
                .map(ReferenceChunk::preview)
                .filter(Objects::nonNull)
                .reduce("", (left, right) -> left + "\n" + right);
        return matchedKeywords(combined, expectedKeywords);
    }

    private List<String> matchedKeywords(String content, List<String> expectedKeywords) {
        if (!hasText(content)) {
            return Collections.emptyList();
        }
        String normalized = content.toLowerCase(Locale.ROOT);
        Set<String> matched = new LinkedHashSet<>();
        for (String keyword : safeKeywords(expectedKeywords)) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                matched.add(keyword);
            }
        }
        return List.copyOf(matched);
    }

    private List<String> safeKeywords(List<String> keywords) {
        if (keywords == null) {
            return Collections.emptyList();
        }
        return keywords.stream()
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private int safeTopK(Integer topK) {
        if (topK == null || topK <= 0) {
            return DEFAULT_TOP_K;
        }
        return Math.min(topK, 20);
    }

    private int count(List<RagEvalCaseResult> results, java.util.function.Predicate<RagEvalCaseResult> predicate) {
        return (int) results.stream().filter(predicate).count();
    }

    private double rate(int count, int total) {
        if (total == 0) {
            return 0.0;
        }
        return Math.round((count * 1.0 / total) * 10000.0) / 10000.0;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
