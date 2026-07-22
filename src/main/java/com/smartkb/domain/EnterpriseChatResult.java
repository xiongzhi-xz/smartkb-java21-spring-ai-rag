package com.smartkb.domain;

import java.util.List;
import java.util.UUID;

public record EnterpriseChatResult(String answer, String rewrittenQuery, List<ReferenceChunk> references,
                                   String retrievalMode, UUID traceId, long latencyMs) { }
