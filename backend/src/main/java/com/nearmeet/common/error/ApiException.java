package com.nearmeet.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base class for errors we expect and want to show to the client as a 4xx.
 * The handler turns it into a ProblemDetail.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    /** short machine-readable name, ends up in the problem "type" */
    public String code() {
        return code;
    }
}
