package com.hyperlocal.delivery.exception;

/**
 * Thrown when a shipment cannot be found or is not visible to the caller
 * (e.g. belongs to a different tenant).
 */
public class ShipmentNotFoundException extends DomainException {

    public ShipmentNotFoundException(String message) {
        super(ErrorCode.SHIPMENT_NOT_FOUND, message);
    }

    public ShipmentNotFoundException(Long shipmentId) {
        super(ErrorCode.SHIPMENT_NOT_FOUND, "Shipment not found: " + shipmentId);
    }
}
