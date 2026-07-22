package com.smartkb.application;

/** Both enterprise retrieval backends failed, so a legacy index must not be used as fallback. */
public class RetrievalUnavailableException extends RuntimeException {

    public RetrievalUnavailableException(Throwable denseFailure, Throwable keywordFailure) {
        super("RETRIEVAL_UNAVAILABLE", denseFailure);
        addSuppressed(keywordFailure);
    }
}
