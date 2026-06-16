package com.smartkb.agent.domain;

import org.springframework.http.HttpStatus;

public class AgentTaskException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public AgentTaskException(String code, HttpStatus status, String message) {
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
