package com.hyperlocal.delivery.exception;

/**
 * Thrown when an invite token is unknown, already spent, or past its expiry.
 *
 * <p>Deliberately one exception for all three: telling a caller which of those
 * applies lets them probe for valid tokens. The frontend shows a single
 * "this link no longer works, ask for a new one" state.
 */
public class InvalidInviteTokenException extends DomainException {

    public InvalidInviteTokenException() {
        super(ErrorCode.INVALID_INVITE_TOKEN, "This invite link is no longer valid.");
    }
}
