package com.smartkb.application.port.outbound;

import java.util.UUID;

/** 按企业文档 ID 清理检索索引。 */
public interface DocumentIndexCleaner {

    int deleteByDocumentId(UUID documentId);
}
