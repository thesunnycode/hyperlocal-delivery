-- V6__architecture_integrity_fixes.sql
-- Structural integrity improvements identified in the architecture audit.

-- 1. Add optimistic locking version column to shipments.
--    Prevents silent lost-updates on any non-locked code path.
ALTER TABLE shipments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- 2. Add unique constraint on delivery_attempts (shipment_id, attempt_number).
--    Prevents duplicate attempt numbers from entering the database even if
--    application-level sequencing has a bug.
ALTER TABLE delivery_attempts
    ADD CONSTRAINT uq_attempts_shipment_number UNIQUE (shipment_id, attempt_number);

-- 3. Drop the dead password_reset_tokens table.
--    The password-reset flow was migrated to OTP + JWT session tokens in V5.
--    This table has no active writers — only the cleanup job touched it.
DROP TABLE IF EXISTS password_reset_tokens;
