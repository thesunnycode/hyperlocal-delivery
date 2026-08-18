package com.hyperlocal.delivery.exception;

/**
 * Base type for all typed domain exceptions. Carries an {@link ErrorCode}
 * so the global exception handler can select the correct HTTP status and
 * response code without instance-of chains.
 */
public abstract class DomainException extends RuntimeException {

    private final ErrorCode code;

    protected DomainException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Error code that classifies this exception.
     */
    public ErrorCode getCode() {
        return code;
    }
}
