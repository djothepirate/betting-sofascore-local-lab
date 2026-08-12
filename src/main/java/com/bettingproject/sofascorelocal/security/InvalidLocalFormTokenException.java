package com.bettingproject.sofascorelocal.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public final class InvalidLocalFormTokenException extends RuntimeException {

    public InvalidLocalFormTokenException() {
        super("INVALID_LOCAL_FORM_TOKEN");
    }
}
