package com.hyperlocal.delivery.exception;

/**
 * Thrown when a request fails business-level validation (e.g. invalid date
 * range). Maps to HTTP 400 via {@link ErrorCode#VALIDATION_ERROR}.
 */
public class ValidationException extends DomainException {

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }
}
