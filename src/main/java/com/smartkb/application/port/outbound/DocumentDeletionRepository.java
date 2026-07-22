package com.smartkb.application.port.outbound;

import com.smartkb.domain.DocumentIngestionSubmission;

import java.util.Optional;
import java.util.UUID;

/** 企业文档删除所需的加锁读取和事实删除端口。 */
public interface DocumentDeletionRepository {

    Optional<DocumentIngestionSubmission> lockLatest(UUID documentId);

    boolean delete(UUID documentId, UUID jobId);
}
