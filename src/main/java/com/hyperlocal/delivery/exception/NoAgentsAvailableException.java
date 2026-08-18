package com.hyperlocal.delivery.exception;

/**
 * Thrown by the auto-assignment path when no active delivery agent exists
 * for the tenant.
 */
public class NoAgentsAvailableException extends DomainException {

    public NoAgentsAvailableException() {
        super(ErrorCode.NO_AGENTS_AVAILABLE, "No active delivery agents available for auto-assignment");
    }

    public NoAgentsAvailableException(String message) {
        super(ErrorCode.NO_AGENTS_AVAILABLE, message);
    }
}
