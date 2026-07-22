package com.smartkb.domain;

import java.time.Instant;
import java.util.UUID;

public record RetrievalTrace(UUID id, UUID assistantMessageId, String query, String candidatesJson,
                             String retrievalMode, long latencyMs, Instant createdAt) { }
