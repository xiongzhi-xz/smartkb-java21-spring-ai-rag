package com.smartkb.application;

/** Identifies the failed Phase 3 indexing boundary for retry and operations. */
public class IndexingFailureException extends RuntimeException {
    private final String errorCode;

    public IndexingFailureException(String errorCode, Throwable cause) {
        super(cause.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
