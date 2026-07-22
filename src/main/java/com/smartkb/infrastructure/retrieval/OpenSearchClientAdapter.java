package com.smartkb.infrastructure.retrieval;

import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Infrastructure gateway for OpenSearch lifecycle checks before index operations are added. */
@Component
@RequiredArgsConstructor
public class OpenSearchClientAdapter {

    private final OpenSearchClient openSearchClient;

    public boolean isAvailable() throws IOException {
        return openSearchClient.ping().value();
    }
}
