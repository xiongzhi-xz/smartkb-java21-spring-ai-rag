package com.smartkb.application;

import com.smartkb.application.port.outbound.DenseVectorIndex;
import com.smartkb.application.port.outbound.KeywordIndex;
import com.smartkb.domain.EnterpriseRetrievalResult;
import com.smartkb.domain.FusedRetrievalCandidate;
import com.smartkb.domain.RetrievalCandidate;
import com.smartkb.domain.RetrievalRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

/** Runs independent enterprise indexes concurrently and applies the Phase 3 weighted RRF contract. */
@Service
@RequiredArgsConstructor
public class EnterpriseRetrievalService {

    private static final double DENSE_WEIGHT = 0.55;
    private static final double KEYWORD_WEIGHT = 0.45;
    private static final int RRF_K = 60;

    private final DenseVectorIndex denseVectorIndex;
    private final KeywordIndex keywordIndex;

    public EnterpriseRetrievalResult retrieve(RetrievalRequest request) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<List<RetrievalCandidate>> dense = CompletableFuture.supplyAsync(
                    () -> denseVectorIndex.search(request), executor);
            CompletableFuture<List<RetrievalCandidate>> keyword = CompletableFuture.supplyAsync(
                    () -> keywordIndex.search(request), executor);
            SearchOutcome denseOutcome = await(dense, "milvus");
            SearchOutcome keywordOutcome = await(keyword, "opensearch");
            if (denseOutcome.failure() != null && keywordOutcome.failure() != null) {
                throw new RetrievalUnavailableException(denseOutcome.failure(), keywordOutcome.failure());
            }

            List<RetrievalCandidate> denseCandidates = denseOutcome.candidates() == null
                    ? List.of() : filter(request, denseOutcome.candidates());
            List<RetrievalCandidate> keywordCandidates = keywordOutcome.candidates() == null
                    ? List.of() : filter(request, keywordOutcome.candidates());
            List<String> failures = new ArrayList<>();
            if (denseOutcome.failure() != null) failures.add("milvus: " + denseOutcome.failure().getClass().getSimpleName());
            if (keywordOutcome.failure() != null) failures.add("opensearch: " + keywordOutcome.failure().getClass().getSimpleName());
            String mode = denseOutcome.failure() != null ? "keyword-only"
                    : keywordOutcome.failure() != null ? "dense-only" : "hybrid";
            return new EnterpriseRetrievalResult(mode, fuse(denseCandidates, keywordCandidates, request.candidateTopK()), failures);
        }
    }

    private SearchOutcome await(CompletableFuture<List<RetrievalCandidate>> future, String backend) {
        try {
            return new SearchOutcome(future.get(), null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new SearchOutcome(null, new IllegalStateException(backend + " search interrupted", exception));
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            return new SearchOutcome(null, cause instanceof RuntimeException runtime ? runtime
                    : new IllegalStateException(backend + " search failed", cause));
        }
    }

    private List<RetrievalCandidate> filter(RetrievalRequest request, List<RetrievalCandidate> candidates) {
        Set<java.util.UUID> documentIds = new HashSet<>(request.documentIds());
        return candidates.stream()
                .filter(candidate -> candidate.knowledgeBaseId().equals(request.knowledgeBaseId()))
                .filter(candidate -> documentIds.isEmpty() || documentIds.contains(candidate.documentId()))
                .limit(request.candidateTopK())
                .toList();
    }

    private List<FusedRetrievalCandidate> fuse(List<RetrievalCandidate> dense, List<RetrievalCandidate> keyword, int topK) {
        Map<java.util.UUID, RankedCandidate> merged = new HashMap<>();
        addRanks(merged, dense, true);
        addRanks(merged, keyword, false);
        return merged.values().stream()
                .map(value -> new FusedRetrievalCandidate(value.candidate(), value.denseRank(), value.keywordRank(),
                        score(value.denseRank(), DENSE_WEIGHT) + score(value.keywordRank(), KEYWORD_WEIGHT)))
                .sorted(Comparator.comparingDouble(FusedRetrievalCandidate::fusionScore).reversed()
                        .thenComparing(value -> value.candidate().chunkId()))
                .limit(topK)
                .toList();
    }

    private void addRanks(Map<java.util.UUID, RankedCandidate> merged, List<RetrievalCandidate> candidates, boolean dense) {
        for (int index = 0; index < candidates.size(); index++) {
            RetrievalCandidate candidate = candidates.get(index);
            int rank = index + 1;
            merged.compute(candidate.chunkId(), (ignored, previous) -> previous == null
                    ? new RankedCandidate(candidate, dense ? rank : null, dense ? null : rank)
                    : dense && previous.denseRank() == null ? previous.withDenseRank(rank)
                    : !dense && previous.keywordRank() == null ? previous.withKeywordRank(rank) : previous);
        }
    }

    private double score(Integer rank, double weight) {
        return rank == null ? 0 : weight / (RRF_K + rank);
    }

    private record SearchOutcome(List<RetrievalCandidate> candidates, Throwable failure) { }

    private record RankedCandidate(RetrievalCandidate candidate, Integer denseRank, Integer keywordRank) {
        RankedCandidate withDenseRank(int rank) { return new RankedCandidate(candidate, rank, keywordRank); }
        RankedCandidate withKeywordRank(int rank) { return new RankedCandidate(candidate, denseRank, rank); }
    }
}
