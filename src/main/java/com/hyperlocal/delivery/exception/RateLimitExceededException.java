package com.hyperlocal.delivery.exception;

/**
 * Thrown when a caller has exceeded the allowed OTP generation requests
 * within the sliding rate-limit window. Maps to HTTP 429 via
 * {@link ErrorCode#RATE_LIMITED}.
 */
public class RateLimitExceededException extends DomainException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED,
                "Rate limit exceeded. Try again in " + retryAfterSeconds + " seconds.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Number of seconds the caller must wait before the next request is allowed.
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
