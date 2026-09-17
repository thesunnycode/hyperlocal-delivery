package com.hyperlocal.delivery.exception;

import com.hyperlocal.delivery.model.ShipmentStatus;

/**
 * Thrown when deletion is attempted on a shipment that is not currently
 * {@code CANCELLED}.
 */
public class InvalidStateForDeleteException extends DomainException {

    public InvalidStateForDeleteException(ShipmentStatus current) {
        super(
                ErrorCode.INVALID_STATE_FOR_DELETE,
                "Deleting a shipment requires it to be CANCELLED; current status is " + current);
    }
}
