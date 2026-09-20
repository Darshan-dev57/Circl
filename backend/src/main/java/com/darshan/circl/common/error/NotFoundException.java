package com.darshan.circl.common.error;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String what, Object id) {
        super(HttpStatus.NOT_FOUND, "not-found", what + " " + id + " was not found");
    }
}
