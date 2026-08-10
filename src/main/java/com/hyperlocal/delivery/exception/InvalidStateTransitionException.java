package com.hyperlocal.delivery.exception;

import com.hyperlocal.delivery.model.ShipmentStatus;

/**
 * Thrown when a caller attempts to move a shipment to a status that is not
 * reachable from its current status under the defined state machine.
 */
public class InvalidStateTransitionException extends DomainException {

    public InvalidStateTransitionException(ShipmentStatus from, ShipmentStatus to) {
        super(
                ErrorCode.INVALID_STATE_TRANSITION,
                "Cannot transition from " + from + " to " + to);
    }
}
