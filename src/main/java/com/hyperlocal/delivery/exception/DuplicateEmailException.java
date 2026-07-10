package com.hyperlocal.delivery.exception;

/**
 * Thrown when creating a user or business with an email that already
 * exists in the system.
 */
public class DuplicateEmailException extends DomainException {

    public DuplicateEmailException(String email) {
        super(ErrorCode.DUPLICATE_EMAIL, "Email already in use: " + email);
    }
}
