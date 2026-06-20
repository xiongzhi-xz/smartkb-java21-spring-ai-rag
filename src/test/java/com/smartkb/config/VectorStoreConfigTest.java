package com.smartkb.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VectorStoreConfigTest {

    @Test
    void vectorStoreUsesConfiguredDimensionsWithoutModelProbe() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Integer.class)))
                .thenThrow(new IllegalStateException("table missing"));

        VectorStoreConfig config = new VectorStoreConfig(jdbcTemplate, new NoProbeEmbeddingModel());
        ReflectionTestUtils.setField(config, "expectedDimensions", 768);

        PgVectorStore vectorStore = config.vectorStore();

        assertNotNull(vectorStore);
        vectorStore.afterPropertiesSet();
    }

    private static final class NoProbeEmbeddingModel implements EmbeddingModel {
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            fail("VectorStore initialization must not call the embedding model");
            return null;
        }

        @Override
        public List<Double> embed(Document document) {
            fail("VectorStore initialization must not call the embedding model");
            return List.of();
        }
    }
}
