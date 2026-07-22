package com.smartkb.infrastructure.retrieval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalIndexPropertiesTest {

    @Test
    void shouldExposeLocalDevelopmentDefaults() {
        RetrievalIndexProperties properties = new RetrievalIndexProperties();

        assertThat(properties.getMilvus().getHost()).isEqualTo("localhost");
        assertThat(properties.getMilvus().getPort()).isEqualTo(19530);
        assertThat(properties.getMilvus().getEmbeddingDimensions()).isEqualTo(768);
        assertThat(properties.getOpensearch().getEndpoint()).isEqualTo("http://localhost:9200");
    }
}
