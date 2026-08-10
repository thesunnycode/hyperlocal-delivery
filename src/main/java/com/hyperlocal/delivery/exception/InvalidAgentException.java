package com.hyperlocal.delivery.exception;

/**
 * Thrown when an assignment targets an agent that is ineligible, for
 * example because the referenced user is not a delivery agent, is
 * inactive, or belongs to a different tenant.
 */
public class InvalidAgentException extends DomainException {

    public InvalidAgentException(String message) {
        super(ErrorCode.INVALID_AGENT, message);
    }
}
