package com.smartkb.agent.domain;

import org.springframework.http.HttpStatus;

/**
 * Domain exception for Project Intake API.
 */
public class ProjectIntakeException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ProjectIntakeException(String code, HttpStatus status, String message) {
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
