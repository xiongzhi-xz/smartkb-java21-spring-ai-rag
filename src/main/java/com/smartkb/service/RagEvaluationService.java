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
        int baselineTop1Hits = count(results, RagEvalCaseResult::baselineTop1Hit);
        int advancedTop1Hits = count(results, RagEvalCaseResult::advancedTop1Hit);

        return new RagEvalReport(
                total,
                baselineHits,
                advancedHits,
                citationHits,
                improvements,
                baselineTop1Hits,
                advancedTop1Hits,
                rate(baselineHits, total),
                rate(advancedHits, total),
                rate(citationHits, total),
                rate(baselineHits, total),
                rate(advancedHits, total),
                averageMrr(results, true),
                averageMrr(results, false),
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

        List<String> expectedChunkIds = safeChunkIds(evalCase.expectedChunkIds());
        boolean hasExpectedChunks = !expectedChunkIds.isEmpty();
        List<String> baselineMatchedChunks = matchedDocumentChunkIds(baselineDocs, expectedChunkIds);
        List<String> advancedMatchedChunks = matchedDocumentChunkIds(advanced.documents(), expectedChunkIds);
        int baselineRank = firstHitRank(baselineDocs, expectedChunkIds);
        int advancedRank = firstHitRank(advanced.documents(), expectedChunkIds);

        List<String> baselineMatched = matchedDocumentKeywords(baselineDocs, evalCase.expectedKeywords());
        List<String> advancedMatched = matchedDocumentKeywords(advanced.documents(), evalCase.expectedKeywords());
        boolean baselineHit = hasExpectedChunks ? baselineRank > 0 : !baselineMatched.isEmpty();
        boolean advancedHit = hasExpectedChunks ? advancedRank > 0 : !advancedMatched.isEmpty();
        boolean citationHit = citationHit(advanced.references(), expectedChunkIds, evalCase.expectedKeywords());

        return new RagEvalCaseResult(
                evalCase.id(),
                evalCase.question(),
                evalCase.expectedFileName(),
                expectedChunkIds,
                safeKeywords(evalCase.expectedKeywords()),
                baselineHit,
                advancedHit,
                citationHit,
                baselineDocs.size(),
                advanced.documents().size(),
                baselineMatchedChunks,
                advancedMatchedChunks,
                baselineRank,
                advancedRank,
                baselineRank == 1,
                advancedRank == 1,
                reciprocalRank(baselineRank),
                reciprocalRank(advancedRank),
                advanced.rewrittenQuery(),
                baselineMatched,
                advancedMatched,
                failureReason(hasExpectedChunks, baselineHit, advancedHit, advancedRank, citationHit),
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

    private boolean citationHit(List<ReferenceChunk> references, List<String> expectedChunkIds, List<String> expectedKeywords) {
        if (!expectedChunkIds.isEmpty()) {
            Set<String> expected = new LinkedHashSet<>(expectedChunkIds);
            boolean chunkHit = references.stream()
                    .map(ReferenceChunk::chunkId)
                    .filter(Objects::nonNull)
                    .anyMatch(expected::contains);
            if (chunkHit) {
                return true;
            }
        }
        return !matchedReferenceKeywords(references, expectedKeywords).isEmpty();
    }

    private List<String> matchedDocumentChunkIds(List<Document> documents, List<String> expectedChunkIds) {
        if (expectedChunkIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> expected = new LinkedHashSet<>(expectedChunkIds);
        Set<String> matched = new LinkedHashSet<>();
        for (Document document : documents) {
            String chunkId = documentChunkId(document);
            if (expected.contains(chunkId)) {
                matched.add(chunkId);
            }
        }
        return List.copyOf(matched);
    }

    private int firstHitRank(List<Document> documents, List<String> expectedChunkIds) {
        if (expectedChunkIds.isEmpty()) {
            return 0;
        }
        Set<String> expected = new LinkedHashSet<>(expectedChunkIds);
        for (int i = 0; i < documents.size(); i++) {
            if (expected.contains(documentChunkId(documents.get(i)))) {
                return i + 1;
            }
        }
        return 0;
    }

    private String documentChunkId(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        if (metadata == null) {
            return String.valueOf(document.getId());
        }
        Object evalChunkId = metadata.get("evalChunkId");
        if (evalChunkId != null && hasText(String.valueOf(evalChunkId))) {
            return String.valueOf(evalChunkId).trim();
        }
        Object chunkId = metadata.get("chunkId");
        if (chunkId != null && hasText(String.valueOf(chunkId))) {
            return String.valueOf(chunkId).trim();
        }
        Object chunkIndex = metadata.get("chunkIndex");
        if (chunkIndex != null && hasText(String.valueOf(chunkIndex))) {
            try {
                return String.format("chunk-%02d", Integer.parseInt(String.valueOf(chunkIndex).trim()));
            } catch (NumberFormatException ignored) {
                return String.valueOf(chunkIndex).trim();
            }
        }
        return String.valueOf(document.getId());
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

    private List<String> safeChunkIds(List<String> chunkIds) {
        if (chunkIds == null) {
            return Collections.emptyList();
        }
        return chunkIds.stream()
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

    private double reciprocalRank(int rank) {
        if (rank <= 0) {
            return 0.0;
        }
        return Math.round((1.0 / rank) * 10000.0) / 10000.0;
    }

    private double averageMrr(List<RagEvalCaseResult> results, boolean baseline) {
        if (results.isEmpty()) {
            return 0.0;
        }
        double total = results.stream()
                .mapToDouble(result -> baseline ? result.baselineMrr() : result.advancedMrr())
                .sum();
        return Math.round((total / results.size()) * 10000.0) / 10000.0;
    }

    private String failureReason(
            boolean hasExpectedChunks,
            boolean baselineHit,
            boolean advancedHit,
            int advancedRank,
            boolean citationHit) {
        if (!hasExpectedChunks) {
            return "未配置预期片段，已回退到关键词覆盖";
        }
        if (!advancedHit && baselineHit) {
            return "Advanced 未命中预期片段，普通召回命中";
        }
        if (!advancedHit) {
            return "Advanced 未命中预期片段";
        }
        if (advancedRank > 1) {
            return "命中预期片段，但未排在首位";
        }
        if (!citationHit) {
            return "引用片段未覆盖预期关键词";
        }
        return "通过";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
