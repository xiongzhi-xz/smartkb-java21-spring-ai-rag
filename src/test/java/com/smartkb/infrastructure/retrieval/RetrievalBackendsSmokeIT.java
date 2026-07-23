package com.smartkb.infrastructure.retrieval;

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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Real-backend smoke test for the Compose Milvus and OpenSearch services.
 *
 * <p>This is intentionally a small round-trip check, not a load test. It records
 * the observed operation latency and any failure in a local Markdown report.</p>
 */
class RetrievalBackendsSmokeIT {

    @Test
    void shouldUpsertSearchAndDeleteAgainstComposeBackends() throws Exception {
        String collection = "smartkb_smoke_" + UUID.randomUUID().toString().replace("-", "");
        String index = "smartkb-smoke-" + UUID.randomUUID().toString().replace("-", "");
        String milvusHost = property("smartkb.smoke.milvus.host", "localhost");
        int milvusPort = Integer.parseInt(property("smartkb.smoke.milvus.port", "19530"));
        String openSearchEndpoint = property("smartkb.smoke.opensearch.endpoint", "http://localhost:9200");
        Path report = Path.of(property("smartkb.smoke.report", "target/reports/retrieval-backends-smoke.md"));
        List<Observation> observations = new ArrayList<>();
        Throwable failure = null;
        MilvusServiceClient milvusClient = null;
        RestClient openSearchRestClient = null;
        RestClientTransport openSearchTransport = null;

        UUID knowledgeBaseId = UUID.randomUUID();
        UUID otherKnowledgeBaseId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID targetChunkId = UUID.randomUUID();
        List<Float> queryVector = List.of(1f, 0f, 0f);
        IndexableChunk target = new IndexableChunk(targetChunkId, documentId, knowledgeBaseId, 1, 0,
                "target-hash", "hybrid retrieval smoke target", queryVector);
        IndexableChunk other = new IndexableChunk(UUID.randomUUID(), UUID.randomUUID(), otherKnowledgeBaseId, 1, 0,
                "other-hash", "unrelated knowledge base content", List.of(0f, 1f, 0f));
        RetrievalRequest retrievalRequest = new RetrievalRequest("hybrid retrieval smoke target", knowledgeBaseId, List.of(), 3);

        try {
            RetrievalIndexProperties properties = new RetrievalIndexProperties();
            properties.getMilvus().setHost(milvusHost);
            properties.getMilvus().setPort(milvusPort);
            properties.getMilvus().setCollection(collection);
            properties.getMilvus().setEmbeddingDimensions(queryVector.size());
            properties.getOpensearch().setEndpoint(openSearchEndpoint);
            properties.getOpensearch().setIndex(index);

            EmbeddingService embeddingService = mock(EmbeddingService.class);
            when(embeddingService.embedText(retrievalRequest.query())).thenReturn(List.of(1d, 0d, 0d));

            milvusClient = new MilvusServiceClient(ConnectParam.newBuilder()
                    .withHost(milvusHost).withPort(milvusPort).build());
            MilvusDenseVectorIndex dense = new MilvusDenseVectorIndex(milvusClient, properties, embeddingService);
            openSearchRestClient = RestClient.builder(HttpHost.create(openSearchEndpoint)).build();
            openSearchTransport = new RestClientTransport(openSearchRestClient, new JacksonJsonpMapper());
            OpenSearchClient openSearchClient = new OpenSearchClient(openSearchTransport);
            OpenSearchKeywordIndex keyword = new OpenSearchKeywordIndex(openSearchClient, properties);

            measure(observations, "milvus", "upsert", () -> dense.upsert(List.of(target, other)));
            List<RetrievalCandidate> denseResults = measure(observations, "milvus", "search", () -> dense.search(retrievalRequest));
            assertThat(denseResults).extracting(RetrievalCandidate::chunkId).contains(targetChunkId);

            measure(observations, "opensearch", "upsert", () -> keyword.upsert(List.of(target, other)));
            List<RetrievalCandidate> keywordResults = measure(observations, "opensearch", "search", () -> keyword.search(retrievalRequest));
            assertThat(keywordResults).extracting(RetrievalCandidate::chunkId).contains(targetChunkId);

            measure(observations, "milvus", "deleteByDocumentId", () -> dense.deleteByDocumentId(documentId));
            measure(observations, "opensearch", "deleteByDocumentId", () -> keyword.deleteByDocumentId(documentId));
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            try {
                if (milvusClient != null) {
                    milvusClient.dropCollection(DropCollectionParam.newBuilder().withCollectionName(collection).build());
                }
            } catch (Exception cleanupFailure) {
                observations.add(new Observation("milvus", "cleanup", 0, "FAILED", cleanupFailure.getMessage()));
            }
            try {
                if (openSearchTransport != null) {
                    OpenSearchClient cleanupClient = new OpenSearchClient(openSearchTransport);
                    cleanupClient.indices().delete(request -> request.index(index));
                }
            } catch (Exception cleanupFailure) {
                observations.add(new Observation("opensearch", "cleanup", 0, "FAILED", cleanupFailure.getMessage()));
            }
            closeQuietly(openSearchTransport);
            closeQuietly(openSearchRestClient);
            closeMilvusQuietly(milvusClient);
            writeReport(report, collection, index, milvusHost + ":" + milvusPort, openSearchEndpoint, observations, failure);
        }
    }

    private static <T> T measure(List<Observation> observations, String backend, String operation, Supplier<T> action) {
        long started = System.nanoTime();
        try {
            T result = action.get();
            observations.add(new Observation(backend, operation, elapsedMillis(started), "PASS", ""));
            return result;
        } catch (RuntimeException exception) {
            observations.add(new Observation(backend, operation, elapsedMillis(started), "FAILED", message(exception)));
            throw exception;
        }
    }

    private static void measure(List<Observation> observations, String backend, String operation, Runnable action) {
        measure(observations, backend, operation, () -> {
            action.run();
            return null;
        });
    }

    private static void writeReport(Path report, String collection, String index, String milvusEndpoint,
                                    String openSearchEndpoint, List<Observation> observations, Throwable failure) {
        try {
            Files.createDirectories(report.toAbsolutePath().getParent());
            StringBuilder output = new StringBuilder("# Retrieval backend smoke report\n\n")
                    .append("- Generated at: ").append(Instant.now()).append('\n')
                    .append("- Scope: one deterministic upsert/search/delete round trip; not a load test\n")
                    .append("- Milvus: `").append(milvusEndpoint).append("`, collection `").append(collection).append("`\n")
                    .append("- OpenSearch: `").append(openSearchEndpoint).append("`, index `").append(index).append("`\n\n")
                    .append("| Backend | Operation | Observed ms | Status | Error |\n")
                    .append("| --- | --- | ---: | --- | --- |\n");
            for (Observation observation : observations) {
                output.append('|').append(observation.backend()).append('|').append(observation.operation())
                        .append('|').append(observation.elapsedMs()).append('|').append(observation.status())
                        .append('|').append(observation.error()).append("|\n");
            }
            output.append("\nResult: `").append(failure == null ? "PASS" : "FAILED").append("`\n");
            if (failure != null) {
                output.append("\nFailure: `").append(message(failure)).append("`\n");
            }
            output.append("\nNo QPS, percentile, or capacity claim is inferred from this smoke run.\n");
            Files.writeString(report, output.toString());
        } catch (Exception reportFailure) {
            System.err.println("Unable to write retrieval smoke report: " + reportFailure.getMessage());
        }
    }

    private static String property(String name, String fallback) {
        return System.getProperty(name, System.getenv().getOrDefault(name, fallback));
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private static String message(Throwable exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message.replace('|', '/').replace('\n', ' ');
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Report already contains the operation failure; cleanup is best effort.
        }
    }

    private static void closeMilvusQuietly(MilvusServiceClient client) {
        if (client == null) return;
        try {
            client.close();
        } catch (Exception ignored) {
            // Cleanup is best effort.
        }
    }

    private record Observation(String backend, String operation, long elapsedMs, String status, String error) {
    }
}
