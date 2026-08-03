package com.aegisteam.aegis.core.exception;

/**
 * Thrown when a refresh token is expired, malformed, or has already been
 * revoked (found in the Redis blocklist). Mapped to HTTP 401 — the client
 * should treat this as "log in again," not retry.
 */
public class TokenRefreshException extends RuntimeException {

    public TokenRefreshException(String message) {
        super(message);
    }
}
