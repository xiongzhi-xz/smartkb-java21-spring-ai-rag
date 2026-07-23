package com.smartkb.infrastructure.retrieval;

import io.milvus.client.MilvusServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Infrastructure gateway for Milvus lifecycle checks without forcing eager startup connectivity. */
@Component
@RequiredArgsConstructor
public class MilvusClientAdapter {

    private final ObjectProvider<MilvusServiceClient> milvusClientProvider;

    public boolean isAvailable() {
        try {
            return milvusClientProvider.getObject().clientIsReady();
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
