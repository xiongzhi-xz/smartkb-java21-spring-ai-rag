package com.smartkb.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingServiceTest {

    @Test
    void embedDocumentsBatch_sendsSingleInputPerOllamaRequest() {
        RecordingEmbeddingModel embeddingModel = new RecordingEmbeddingModel();
        EmbeddingService service = new EmbeddingService(embeddingModel);
        List<Document> documents = List.of(
                new Document("chunk one"),
                new Document("chunk two"),
                new Document("chunk three")
        );

        service.embedDocumentsBatch(documents);

        assertEquals(3, embeddingModel.batchSizes().size());
        assertTrue(embeddingModel.batchSizes().stream().allMatch(size -> size == 1),
                "Ollama Embedding should receive one chunk per request");
        assertTrue(documents.stream().allMatch(document -> document.getEmbedding() != null));
    }

    private static final class RecordingEmbeddingModel implements EmbeddingModel {
        private final List<Integer> batchSizes = Collections.synchronizedList(new ArrayList<>());

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<String> inputs = request.getInstructions();
            batchSizes.add(inputs.size());
            List<Embedding> embeddings = new ArrayList<>();
            for (int i = 0; i < inputs.size(); i++) {
                embeddings.add(new Embedding(List.of(0.1, 0.2, 0.3), i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public List<Double> embed(Document document) {
            return List.of(0.1, 0.2, 0.3);
        }

        List<Integer> batchSizes() {
            return batchSizes;
        }
    }
}
