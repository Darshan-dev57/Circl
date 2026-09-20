package com.darshan.circl.common.error;

import org.springframework.http.HttpStatus;

/** The request is well formed but breaks a business rule -> 422. */
public class RuleViolationException extends ApiException {

    public RuleViolationException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
