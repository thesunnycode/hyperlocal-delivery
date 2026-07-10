package com.hyperlocal.delivery.exception;

/**
 * Thrown when the caller is authenticated but not authorised to perform
 * the requested operation (e.g. role mismatch, cross-tenant access).
 */
public class ForbiddenException extends DomainException {

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
