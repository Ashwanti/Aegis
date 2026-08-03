package com.aegisteam.aegis.core.exception;

/** Thrown when login email/password don't match. Mapped to HTTP 401. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
