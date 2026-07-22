package com.smartkb.application;

/** 文档当前状态不允许删除，或删除期间状态保护失效。 */
public class DocumentDeletionConflictException extends RuntimeException {

    public DocumentDeletionConflictException(String message) {
        super(message);
    }
}
