package com.hyperlocal.delivery.dto.auth;

/**
 * Error response body for OTP validation failures. Provides structured
 * information about why verification failed.
 *
 * <p>The {@code reason} field indicates the failure type:
 * <ul>
 *   <li>{@code "invalid_code"} — submitted code does not match</li>
 *   <li>{@code "expired"} — OTP verification window has elapsed</li>
 *   <li>{@code "max_attempts"} — maximum verification attempts exceeded</li>
 *   <li>{@code "no_record"} — no active OTP record found for the email/purpose</li>
 * </ul>
 *
 * <p>The {@code attemptsRemaining} field is only populated for
 * {@code "invalid_code"} failures; it is {@code null} for other reason types.
 */
public record OtpErrorResponse(
        String error,
        String reason,
        Integer attemptsRemaining
) {}
