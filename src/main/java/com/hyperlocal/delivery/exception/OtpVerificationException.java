package com.hyperlocal.delivery.exception;

/**
 * Thrown when OTP verification fails. Carries the specific error code and
 * optionally the number of remaining attempts (for INVALID_OTP failures).
 */
public class OtpVerificationException extends DomainException {

    private final Integer attemptsRemaining;

    public OtpVerificationException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public OtpVerificationException(ErrorCode code, String message, Integer attemptsRemaining) {
        super(code, message);
        this.attemptsRemaining = attemptsRemaining;
    }

    /**
     * Remaining verification attempts, or {@code null} if not applicable.
     */
    public Integer getAttemptsRemaining() {
        return attemptsRemaining;
    }
}
