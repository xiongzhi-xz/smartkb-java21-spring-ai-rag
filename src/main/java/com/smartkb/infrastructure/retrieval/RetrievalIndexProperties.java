package com.smartkb.infrastructure.retrieval;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "smartkb.retrieval")
public class RetrievalIndexProperties {

    private final Milvus milvus = new Milvus();
    private final OpenSearch opensearch = new OpenSearch();

    public Milvus getMilvus() {
        return milvus;
    }

    public OpenSearch getOpensearch() {
        return opensearch;
    }

    public static class Milvus {
        private String host = "localhost";
        private int port = 19530;
        private String collection = "smartkb_chunks";
        private int embeddingDimensions = 768;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getCollection() {
            return collection;
        }

        public void setCollection(String collection) {
            this.collection = collection;
        }

        public int getEmbeddingDimensions() {
            return embeddingDimensions;
        }

        public void setEmbeddingDimensions(int embeddingDimensions) {
            this.embeddingDimensions = embeddingDimensions;
        }
    }

    public static class OpenSearch {
        private String endpoint = "http://localhost:9200";
        private String index = "smartkb_chunks";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getIndex() {
            return index;
        }

        public void setIndex(String index) {
            this.index = index;
        }
    }
}
