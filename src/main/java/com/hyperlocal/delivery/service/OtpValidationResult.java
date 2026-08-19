package com.hyperlocal.delivery.service;

/**
 * Result of an OTP validation attempt. This is a sealed interface with two
 * concrete record implementations: {@link Success} and {@link Failure}.
 *
 * <p>Callers pattern-match on the type to determine the outcome:
 * <pre>{@code
 * OtpValidationResult result = otpService.validateOtp(email, code, purpose);
 * if (result instanceof OtpValidationResult.Success) {
 *     // proceed
 * } else {
 *     var failure = (OtpValidationResult.Failure) result;
 *     // handle failure.reason() and failure.attemptsRemaining()
 * }
 * }</pre>
 */
public sealed interface OtpValidationResult permits OtpValidationResult.Success, OtpValidationResult.Failure {

    /**
     * Indicates the OTP was valid and the record has been marked VERIFIED.
     */
    record Success() implements OtpValidationResult {}

    /**
     * Indicates the OTP validation failed.
     *
     * @param reason            machine-readable failure reason
     * @param attemptsRemaining remaining verification attempts (null when
     *                          not applicable, e.g. for expired or no-record)
     */
    record Failure(FailureReason reason, Integer attemptsRemaining) implements OtpValidationResult {}

    /**
     * Machine-readable reasons for OTP validation failure.
     */
    enum FailureReason {
        /** The submitted code does not match the stored hash. */
        INVALID_CODE,
        /** The OTP has expired (past the 5-minute verification window). */
        EXPIRED,
        /** Maximum verification attempts (5) have been reached. */
        MAX_ATTEMPTS,
        /** No active OTP record exists for this email+purpose. */
        NO_RECORD
    }
}
