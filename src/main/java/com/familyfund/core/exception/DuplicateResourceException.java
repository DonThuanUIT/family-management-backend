package com.familyfund.core.exception;

/**
 * Thrown when a request would create data that already exists. Mapped to HTTP 409 Conflict.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
