package com.familyfund.core.exception;

/**
 * Thrown when any layer (JPA lifecycle, repository, database trigger, or HTTP guard)
 * detects an attempted UPDATE, DELETE, overwrite, or hash-chain violation on {@code immutable_ledgers}
 * (NFR-01 / US-61).
 */
public class LedgerImmutabilityViolationException extends RuntimeException {

    public static final String ERROR_CODE = "LEDGER_IMMUTABILITY_VIOLATION";

    public LedgerImmutabilityViolationException(String message) {
        super(message);
    }

    public LedgerImmutabilityViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
