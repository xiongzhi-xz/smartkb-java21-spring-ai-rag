package com.smartkb.infrastructure.retrieval;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RetrievalIndexProperties.class)
public class RetrievalIndexConfig {

    @Bean(destroyMethod = "close")
    MilvusServiceClient milvusClient(RetrievalIndexProperties properties) {
        RetrievalIndexProperties.Milvus milvus = properties.getMilvus();
        return new MilvusServiceClient(ConnectParam.newBuilder()
                .withHost(milvus.getHost())
                .withPort(milvus.getPort())
                .build());
    }

    @Bean(destroyMethod = "")
    RestClient openSearchRestClient(RetrievalIndexProperties properties) {
        return RestClient.builder(HttpHost.create(properties.getOpensearch().getEndpoint())).build();
    }

    @Bean(destroyMethod = "close")
    RestClientTransport openSearchTransport(RestClient openSearchRestClient) {
        return new RestClientTransport(openSearchRestClient, new JacksonJsonpMapper());
    }

    @Bean
    OpenSearchClient openSearchClient(RestClientTransport openSearchTransport) {
        return new OpenSearchClient(openSearchTransport);
    }
}
