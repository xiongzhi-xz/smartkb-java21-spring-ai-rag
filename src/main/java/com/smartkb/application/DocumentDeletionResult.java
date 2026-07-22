package com.smartkb.application;

import java.util.UUID;

/** 企业文档删除结果。 */
public record DocumentDeletionResult(UUID documentId, String fileName, int deletedChunks) {
}
