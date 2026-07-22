package com.smartkb.infrastructure.retrieval;

import io.milvus.client.MilvusServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Infrastructure gateway for Milvus lifecycle checks before index operations are added. */
@Component
@RequiredArgsConstructor
public class MilvusClientAdapter {

    private final MilvusServiceClient milvusClient;

    public boolean isAvailable() {
        return milvusClient.clientIsReady();
    }
}
