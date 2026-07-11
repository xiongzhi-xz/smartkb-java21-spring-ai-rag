package com.smartkb.service;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

@Service
public class BgeRerankerClient {

    private final RestClient restClient;
    private final boolean enabled;

    public BgeRerankerClient(
            RestClient.Builder restClientBuilder,
            @Value("${smartkb.reranker.base-url:http://localhost:8090}") String baseUrl,
            @Value("${smartkb.reranker.enabled:true}") boolean enabled,
            @Value("${smartkb.reranker.timeout:3s}") Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<Document> rerank(String query, List<Document> documents, int topK) {
        if (!enabled || documents.size() < 2) {
            return documents.stream().limit(topK).toList();
        }

        RerankResponse response = restClient.post()
                .uri("/rerank")
                .body(new RerankRequest(query, documents.stream().map(Document::getContent).toList(), topK))
                .retrieve()
                .body(RerankResponse.class);
        if (response == null || response.results() == null || response.results().isEmpty()) {
            throw new IllegalStateException("BGE reranker returned no results");
        }

        return response.results().stream()
                .sorted(Comparator.comparingDouble(RerankItem::score).reversed())
                .map(item -> documentAt(documents, item.index()))
                .toList();
    }

    private Document documentAt(List<Document> documents, int index) {
        if (index < 0 || index >= documents.size()) {
            throw new IllegalStateException("BGE reranker returned an invalid document index: " + index);
        }
        return documents.get(index);
    }

    record RerankRequest(String query, List<String> documents, int topK) {
    }

    record RerankResponse(String model, String device, List<RerankItem> results) {
    }

    record RerankItem(int index, double score) {
    }
}
