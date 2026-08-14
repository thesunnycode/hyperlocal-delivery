-- V2__remove_created_state.sql
-- Removes CREATED from the shipment lifecycle. Per STATE_MACHINE.md, there is
-- no Created state in the product: a shipment is auto-assigned and starts
-- life directly in ASSIGNED. "Created -> Assigned" was always a single system
-- event, never two.
--
-- V1 defaulted shipments.status to 'CREATED' and the application inserted two
-- shipment_events rows (null->CREATED, then CREATED->ASSIGNED) even though the
-- live shipments.status column was always set to ASSIGNED directly at insert
-- (no shipment ever actually had status='CREATED'). This migration:
--   1. Cleans up the historical shipment_events rows left by the old two-row
--      seed.
--   2. Narrows both ENUM columns to drop CREATED and changes the shipments
--      default to ASSIGNED.

-- 1a. Delete the redundant "-> CREATED" seed events.
DELETE FROM shipment_events WHERE to_status = 'CREATED';

-- 1b. The "CREATED -> ASSIGNED" events become each shipment's single genesis
--     event; their from_status becomes NULL (no prior status).
UPDATE shipment_events SET from_status = NULL WHERE from_status = 'CREATED';

-- 2. Narrow the ENUM columns.
ALTER TABLE shipments
    MODIFY status ENUM('ASSIGNED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED','RETURNED')
    NOT NULL DEFAULT 'ASSIGNED';

ALTER TABLE shipment_events
    MODIFY from_status ENUM('ASSIGNED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED','RETURNED')
    NULL DEFAULT NULL;

ALTER TABLE shipment_events
    MODIFY to_status ENUM('ASSIGNED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED','RETURNED')
    NOT NULL;
