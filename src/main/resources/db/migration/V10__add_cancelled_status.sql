-- V10__add_cancelled_status.sql
-- Adds CANCELLED as a terminal shipment status the business owner can
-- trigger directly from any non-terminal status (ASSIGNED, PICKED_UP,
-- IN_TRANSIT, OUT_FOR_DELIVERY), mirroring the existing owner-triggered
-- reassign action's authorization and locking pattern.

ALTER TABLE shipments
    MODIFY status ENUM('ASSIGNED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED','RETURNED','CANCELLED')
    NOT NULL DEFAULT 'ASSIGNED';

ALTER TABLE shipment_events
    MODIFY from_status ENUM('ASSIGNED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED','RETURNED','CANCELLED')
    NULL DEFAULT NULL;

ALTER TABLE shipment_events
    MODIFY to_status ENUM('ASSIGNED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED','RETURNED','CANCELLED')
    NOT NULL;
