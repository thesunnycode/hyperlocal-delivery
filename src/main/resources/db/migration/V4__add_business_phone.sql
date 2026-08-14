-- V4__add_business_phone.sql
-- Adds an optional phone number to businesses, exposed on the public
-- tracking payload as businessPhone -- the one customer-contact channel
-- STATE_MACHINE.md permits ("no contact except the opt-in businessPhone
-- affordance on failed/returned"). Nullable: existing businesses have no
-- phone on record, and the customer surface renders no Call affordance
-- when it's absent.

ALTER TABLE businesses ADD COLUMN phone VARCHAR(20) NULL DEFAULT NULL;
