package com.smartkb.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartkb.application.port.outbound.RetrievalTraceRepository;
import com.smartkb.domain.RetrievalTrace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RetrievalTraceQueryService {
    private final RetrievalTraceRepository repository;
    private final ObjectMapper objectMapper;

    public Map<String, Object> get(UUID traceId) {
        RetrievalTrace trace = repository.findById(traceId)
                .orElseThrow(() -> new IllegalArgumentException("retrieval trace not found: " + traceId));
        try {
            JsonNode candidates = objectMapper.readTree(trace.candidatesJson());
            return Map.of("traceId", trace.id().toString(), "assistantMessageId", trace.assistantMessageId().toString(),
                    "query", trace.query(), "candidates", candidates, "retrievalMode", trace.retrievalMode(),
                    "latencyMs", trace.latencyMs(), "createdAt", trace.createdAt().toString());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("stored retrieval trace is invalid", exception);
        }
    }
}
