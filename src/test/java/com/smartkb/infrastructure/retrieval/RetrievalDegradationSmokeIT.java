package com.smartkb.infrastructure.retrieval;

import com.smartkb.application.EnterpriseRetrievalService;
import com.smartkb.application.RetrievalUnavailableException;
import com.smartkb.domain.IndexableChunk;
import com.smartkb.domain.RetrievalCandidate;
import com.smartkb.domain.RetrievalRequest;
import com.smartkb.service.EmbeddingService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.collection.DropCollectionParam;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Compose-backed fault-injection smoke test for the enterprise retrieval fallback contract.
 *
 * <p>The PowerShell driver runs this test in separate Maven invocations while stopping one
 * or both Compose services. The test itself always uses the production Milvus/OpenSearch
 * adapters; failed endpoints are injected only through their connection properties.</p>
 */
class RetrievalDegradationSmokeIT {

    @Test
    void shouldVerifyRequestedFaultInjectionScenario() {
        String scenario = required("smartkb.degradation.scenario");
        String milvusHost = property("smartkb.degradation.milvus.host", "localhost");
        int milvusPort = Integer.parseInt(property("smartkb.degradation.milvus.port", "19530"));
        String openSearchEndpoint = property("smartkb.degradation.opensearch.endpoint", "http://localhost:9200");
        String collection = required("smartkb.degradation.collection");
        String index = required("smartkb.degradation.index");

        UUID knowledgeBaseId = UUID.fromString(required("smartkb.degradation.knowledgeBaseId"));
        UUID documentId = UUID.fromString(required("smartkb.degradation.documentId"));
        UUID targetChunkId = UUID.fromString(required("smartkb.degradation.chunkId"));
        RetrievalRequest request = new RetrievalRequest("fault injection retrieval target", knowledgeBaseId, List.of(), 3);
        List<IndexableChunk> chunks = List.of(
                new IndexableChunk(targetChunkId, documentId, knowledgeBaseId, 1, 0,
                        "degradation-target-hash", request.query(), List.of(1f, 0f, 0f)),
                new IndexableChunk(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, 0,
                        "degradation-other-hash", "unrelated content", List.of(0f, 1f, 0f)));

        AtomicReference<MilvusServiceClient> milvusClientRef = new AtomicReference<>();
        RestClient openSearchRestClient = null;
        RestClientTransport openSearchTransport = null;
        try {
            RetrievalIndexProperties properties = new RetrievalIndexProperties();
            properties.getMilvus().setHost(milvusHost);
            properties.getMilvus().setPort(milvusPort);
            properties.getMilvus().setCollection(collection);
            properties.getMilvus().setEmbeddingDimensions(3);
            properties.getOpensearch().setEndpoint(openSearchEndpoint);
            properties.getOpensearch().setIndex(index);

            EmbeddingService embeddingService = mock(EmbeddingService.class);
            when(embeddingService.embedText(request.query())).thenReturn(List.of(1d, 0d, 0d));

            MilvusDenseVectorIndex dense = new MilvusDenseVectorIndex(() -> milvusClientRef.updateAndGet(existing ->
                    existing != null ? existing : new MilvusServiceClient(ConnectParam.newBuilder()
                            .withHost(milvusHost).withPort(milvusPort)
                            .withConnectTimeout(3, TimeUnit.SECONDS)
                            .withRpcDeadline(5, TimeUnit.SECONDS)
                            .build())), properties, embeddingService);
            openSearchRestClient = RestClient.builder(HttpHost.create(openSearchEndpoint))
                    .setRequestConfigCallback(config -> config
                            .setConnectTimeout(3_000)
                            .setConnectionRequestTimeout(3_000)
                            .setSocketTimeout(5_000))
                    .build();
            openSearchTransport = new RestClientTransport(openSearchRestClient, new JacksonJsonpMapper());
            OpenSearchClient openSearchClient = new OpenSearchClient(openSearchTransport);
            OpenSearchKeywordIndex keyword = new OpenSearchKeywordIndex(openSearchClient, properties);

            switch (scenario) {
                case "seed" -> seed(dense, keyword, chunks, request, targetChunkId);
                case "keyword-only" -> assertDegradedToKeyword(dense, keyword, request, targetChunkId);
                case "dense-only" -> assertDegradedToDense(dense, keyword, request, targetChunkId);
                case "unavailable" -> assertUnavailable(dense, keyword, request);
                case "cleanup" -> cleanup(milvusClientRef.get(), dense, keyword, documentId, collection, openSearchClient, index);
                default -> throw new IllegalArgumentException("Unsupported degradation scenario: " + scenario);
            }
        } finally {
            closeQuietly(openSearchTransport);
            closeQuietly(openSearchRestClient);
            closeMilvusQuietly(milvusClientRef.get());
        }
    }

    private void seed(MilvusDenseVectorIndex dense, OpenSearchKeywordIndex keyword,
                      List<IndexableChunk> chunks, RetrievalRequest request, UUID targetChunkId) {
        dense.upsert(chunks);
        keyword.upsert(chunks);
        assertThat(dense.search(request)).extracting(RetrievalCandidate::chunkId).contains(targetChunkId);
        assertThat(keyword.search(request)).extracting(RetrievalCandidate::chunkId).contains(targetChunkId);
    }

    private void assertDegradedToKeyword(MilvusDenseVectorIndex dense, OpenSearchKeywordIndex keyword,
                                         RetrievalRequest request, UUID targetChunkId) {
        var result = new EnterpriseRetrievalService(dense, keyword).retrieve(request);
        assertThat(result.mode()).isEqualTo("keyword-only");
        assertThat(result.candidates()).extracting(item -> item.candidate().chunkId()).contains(targetChunkId);
        assertThat(result.backendFailures()).anyMatch(item -> item.startsWith("milvus:"));
    }

    private void assertDegradedToDense(MilvusDenseVectorIndex dense, OpenSearchKeywordIndex keyword,
                                       RetrievalRequest request, UUID targetChunkId) {
        var result = new EnterpriseRetrievalService(dense, keyword).retrieve(request);
        assertThat(result.mode()).isEqualTo("dense-only");
        assertThat(result.candidates()).extracting(item -> item.candidate().chunkId()).contains(targetChunkId);
        assertThat(result.backendFailures()).anyMatch(item -> item.startsWith("opensearch:"));
    }

    private void assertUnavailable(MilvusDenseVectorIndex dense, OpenSearchKeywordIndex keyword,
                                   RetrievalRequest request) {
        assertThatThrownBy(() -> new EnterpriseRetrievalService(dense, keyword).retrieve(request))
                .isInstanceOf(RetrievalUnavailableException.class)
                .hasMessage("RETRIEVAL_UNAVAILABLE");
    }

    private void cleanup(MilvusServiceClient milvusClient, MilvusDenseVectorIndex dense,
                         OpenSearchKeywordIndex keyword, UUID documentId, String collection,
                         OpenSearchClient openSearchClient, String index) {
        dense.deleteByDocumentId(documentId);
        keyword.deleteByDocumentId(documentId);
        try {
            milvusClient.dropCollection(DropCollectionParam.newBuilder().withCollectionName(collection).build());
        } catch (RuntimeException ignored) {
            // Cleanup is best effort; the scenario's assertions already completed.
        }
        try {
            if (openSearchClient.indices().exists(request -> request.index(index)).value()) {
                openSearchClient.indices().delete(request -> request.index(index));
            }
        } catch (Exception ignored) {
            // Cleanup is best effort.
        }
    }

    private static String required(String name) {
        String value = property(name, "");
        if (value.isBlank()) throw new IllegalArgumentException("Missing required property: " + name);
        return value;
    }

    private static String property(String name, String fallback) {
        return System.getProperty(name, System.getenv().getOrDefault(name, fallback));
    }

    private static void closeMilvusQuietly(MilvusServiceClient client) {
        if (client == null) return;
        try {
            client.close();
        } catch (Exception ignored) {
            // Test cleanup is best effort.
        }
    }
    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Test cleanup is best effort.
        }
    }
}
