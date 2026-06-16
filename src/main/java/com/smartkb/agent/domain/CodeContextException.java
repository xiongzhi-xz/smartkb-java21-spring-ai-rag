package com.smartkb.agent.domain;

import org.springframework.http.HttpStatus;

/**
 * Domain exception for Code Context APIs.
 */
public class CodeContextException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public CodeContextException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
