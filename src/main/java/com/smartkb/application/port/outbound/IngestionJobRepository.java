package com.smartkb.application.port.outbound;

import com.smartkb.domain.IngestionJob;

import java.util.Optional;

/** 入库任务状态的持久化端口。 */
public interface IngestionJobRepository {
    IngestionJob createOrGet(IngestionJob job);
    Optional<IngestionJob> findByIdempotencyKey(String idempotencyKey);
    boolean markProcessing(java.util.UUID jobId);
    boolean markReady(java.util.UUID jobId);
    boolean markFailed(java.util.UUID jobId, String errorCode, String errorMessage);
}
