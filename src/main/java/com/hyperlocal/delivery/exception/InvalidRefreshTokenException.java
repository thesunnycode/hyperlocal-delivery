package com.hyperlocal.delivery.exception;

/**
 * Thrown when a refresh-token exchange fails: the token is unknown,
 * expired, or already consumed.
 */
public class InvalidRefreshTokenException extends DomainException {

    public InvalidRefreshTokenException() {
        super(ErrorCode.INVALID_REFRESH_TOKEN, "Invalid or expired refresh token");
    }

    public InvalidRefreshTokenException(String message) {
        super(ErrorCode.INVALID_REFRESH_TOKEN, message);
    }
}
