package com.hyperlocal.delivery.exception;

import com.hyperlocal.delivery.model.ShipmentStatus;

/**
 * Thrown when a delivery attempt is recorded against a shipment that is
 * not currently {@code OUT_FOR_DELIVERY}.
 */
public class InvalidStateForAttemptException extends DomainException {

    public InvalidStateForAttemptException(ShipmentStatus current) {
        super(
                ErrorCode.INVALID_STATE_FOR_ATTEMPT,
                "Delivery attempt requires OUT_FOR_DELIVERY; current status is " + current);
    }
}
