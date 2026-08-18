package com.hyperlocal.delivery.exception;

import org.springframework.http.HttpStatus;

/**
 * Canonical catalogue of machine-readable error codes returned by the API.
 *
 * <p>Each value carries its canonical HTTP status, which the global
 * exception handler uses to write the response. The enum {@code name()}
 * becomes the {@code code} field in the {@code ApiError} envelope.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED),
    INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST),
    INVALID_INVITE_TOKEN(HttpStatus.BAD_REQUEST),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    AGENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    SHIPMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT),
    INVALID_STATE_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY),
    AGENT_HAS_ACTIVE_SHIPMENTS(HttpStatus.UNPROCESSABLE_ENTITY),
    NO_AGENTS_AVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_AGENT(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_STATE_FOR_ATTEMPT(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_OTP(HttpStatus.BAD_REQUEST),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST),
    MAX_ATTEMPTS_EXCEEDED(HttpStatus.BAD_REQUEST),
    NO_PENDING_VERIFICATION(HttpStatus.BAD_REQUEST),
    SESSION_EXPIRED(HttpStatus.BAD_REQUEST),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    EMAIL_DELIVERY_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    /**
     * Canonical HTTP status this error maps to.
     */
    public HttpStatus getStatus() {
        return status;
    }
}
