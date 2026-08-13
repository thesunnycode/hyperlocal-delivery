package com.hyperlocal.delivery.exception;

/**
 * Thrown when a password-reset token is unknown, already used, or expired.
 */
public class InvalidResetTokenException extends DomainException {

    public InvalidResetTokenException() {
        super(ErrorCode.INVALID_RESET_TOKEN, "Reset link is invalid or has expired");
    }
}
