package com.hyperlocal.delivery.exception;

/**
 * Thrown when login fails because the supplied email or password does not
 * match any active account.
 */
public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
    }

    public InvalidCredentialsException(String message) {
        super(ErrorCode.INVALID_CREDENTIALS, message);
    }
}
