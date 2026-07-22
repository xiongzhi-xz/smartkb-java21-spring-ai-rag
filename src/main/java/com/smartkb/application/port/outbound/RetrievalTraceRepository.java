package com.smartkb.application.port.outbound;

import java.util.UUID;

/** Durable audit record for the evidence used to produce an enterprise answer. */
public interface RetrievalTraceRepository {
    void save(UUID traceId, UUID assistantMessageId, String query, String candidatesJson,
              String retrievalMode, long latencyMs);
}
